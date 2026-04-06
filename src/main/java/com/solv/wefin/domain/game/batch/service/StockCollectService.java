package com.solv.wefin.domain.game.batch.service;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchStatus;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.kis.KisCandleResponse;
import com.solv.wefin.domain.game.kis.KisStockClient;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollectService {

    private final KisStockClient kisStockClient;
    private final BatchProgressRepository batchProgressRepository;
    private final StockCollectTxService txService;

    private static final LocalDate COLLECT_START = LocalDate.of(2020, 1, 2);
    private static final LocalDate COLLECT_END = LocalDate.of(2024, 12, 31);
    private static final int KIS_MAX_ROWS = 100;
    private static final long API_DELAY_MS = 1000;
    private static final int MAX_RETRIES = 3;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 서버 시작 시 IN_PROGRESS 상태를 PENDING으로 복구한다.
     * 이전 수집 도중 서버가 죽으면 IN_PROGRESS가 영구 누락되기 때문.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInProgressOnStartup() {
        List<BatchProgress> stuckList = batchProgressRepository.findByStatus(BatchStatus.IN_PROGRESS);
        for (BatchProgress bp : stuckList) {
            bp.retry();
            batchProgressRepository.save(bp);
        }
        if (!stuckList.isEmpty()) {
            log.info("[IN_PROGRESS 복구] {}종목을 PENDING으로 전환", stuckList.size());
        }
    }

    /**
     * 비동기로 수집을 실행한다. Controller에서 호출하는 진입점.
     * HTTP 응답을 즉시 반환하고 백그라운드에서 수집 진행.
     */
    @Async("batchExecutor")
    public void collectBatchAsync(int batchSize) {
        collectBatch(batchSize);
    }

    /**
     * FAILED 우선, 부족하면 PENDING 추가하여 batchSize만큼 수집한다.
     * 스케줄러와 수동 트리거 모두 이 메서드를 사용하므로 AtomicBoolean으로 동시 실행 방지.
     */
    public int collectBatch(int batchSize) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[수집 스킵] 이미 수집이 진행 중입니다");
            return 0;
        }

        try {
            return doCollectBatch(batchSize);
        } finally {
            running.set(false);
        }
    }

    private int doCollectBatch(int batchSize) {
        List<BatchProgress> targets = new ArrayList<>(batchProgressRepository.findRetryableFailures(MAX_RETRIES));

        if (targets.size() < batchSize) {
            targets.addAll(batchProgressRepository.findByStatus(BatchStatus.PENDING));
        }

        int limit = Math.min(targets.size(), batchSize);
        int successCount = 0;

        for (int i = 0; i < limit; i++) {
            BatchProgress progress = targets.get(i);
            try {
                collectOneStock(progress);
                successCount++;
            } catch (Exception e) {
                log.error("[수집 실패] 종목={}, 에러={}",
                        progress.getStockInfo().getSymbol(), e.getMessage());
            }

            if (i < limit - 1) {
                try {
                    Thread.sleep(API_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[배치 중단] 인터럽트 발생");
                    break;
                }
            }
        }

        log.info("[배치 완료] 처리={}/{}, 성공={}", limit, targets.size(), successCount);
        return successCount;
    }

    /**
     * 1종목의 5년치 일봉을 수집하여 DB에 저장한다.
     * 100일씩 끊어서 KIS API를 호출하고, 각 호출 사이에 1초 딜레이를 둔다.
     * Thread.sleep이 포함되므로 @Transactional을 걸지 않고,
     * DB 저장은 StockCollectTxService를 통해 별도 트랜잭션으로 처리한다.
     */
    public void collectOneStock(BatchProgress progress) {
        StockInfo stockInfo = progress.getStockInfo();
        String symbol = stockInfo.getSymbol();
        String marketCode = resolveMarketCode(stockInfo.getMarket());

        txService.updateStatus(progress, BatchStatus.IN_PROGRESS);

        try {
            LocalDate lastCollected = progress.getLastCollectedDate();
            if (lastCollected == null) {
                lastCollected = COLLECT_START.minusDays(1);
                log.warn("[lastCollectedDate null] 종목={}, 기본값 사용={}", symbol, lastCollected);
            }
            LocalDate from = lastCollected.plusDays(1);

            if (from.isAfter(COLLECT_END)) {
                log.info("[이미 완료] 종목={}", symbol);
                txService.completeProgress(progress, COLLECT_END);
                return;
            }

            int totalSaved = 0;

            while (!from.isAfter(COLLECT_END)) {
                LocalDate to = from.plusDays(KIS_MAX_ROWS - 1);
                if (to.isAfter(COLLECT_END)) {
                    to = COLLECT_END;
                }

                KisCandleResponse response = kisStockClient.fetchDailyPrice(symbol, marketCode, from, to);

                if (response != null && response.output2() != null) {
                    int saved = txService.saveChunk(stockInfo, response.output2());
                    totalSaved += saved;
                }

                txService.updateLastCollectedDate(progress, to);

                from = to.plusDays(1);

                if (!from.isAfter(COLLECT_END)) {
                    Thread.sleep(API_DELAY_MS);
                }
            }

            txService.completeProgress(progress, COLLECT_END);
            log.info("[수집 완료] 종목={}, 저장={}건", symbol, totalSaved);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            txService.failProgress(progress, "수집 중 인터럽트 발생");
            throw new RuntimeException("수집 중 인터럽트", e);
        } catch (Exception e) {
            txService.failProgress(progress, truncateMessage(e.getMessage(), 500));
            throw e;
        }
    }

    private String resolveMarketCode(String market) {
        if ("KOSDAQ".equalsIgnoreCase(market)) {
            return "Q";
        }
        return "J";
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "알 수 없는 에러";
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
