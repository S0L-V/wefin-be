package com.solv.wefin.domain.game.batch.service;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchStatus;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.kis.KisCandleResponse;
import com.solv.wefin.domain.game.kis.KisStockClient;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollectService {

    private final KisStockClient kisStockClient;
    private final StockDailyRepository stockDailyRepository;
    private final BatchProgressRepository batchProgressRepository;

    private static final LocalDate COLLECT_START = LocalDate.of(2020, 1, 2);
    private static final LocalDate COLLECT_END = LocalDate.of(2024, 12, 31);
    private static final int KIS_MAX_ROWS = 100;
    private static final long API_DELAY_MS = 1000;
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 비동기로 수집을 실행한다. Controller에서 호출하는 진입점.
     * HTTP 응답을 즉시 반환하고 백그라운드에서 수집 진행.
     */
    @Async("batchExecutor")
    public void collectBatchAsync(int batchSize) {
        collectBatch(batchSize);
    }

    /**
     * PENDING 또는 FAILED 상태인 종목을 batchSize만큼 수집한다.
     * 스케줄러에서 직접 호출하는 동기 메서드.
     * 트랜잭션 없이 실행 — 종목 단위로 개별 트랜잭션 처리.
     */
    public int collectBatch(int batchSize) {
        // FAILED 우선 재시도, 부족하면 PENDING 추가
        List<BatchProgress> targets = new ArrayList<>(batchProgressRepository.findByStatus(BatchStatus.FAILED));

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

            // 종목 간 1초 딜레이 (마지막 종목 제외)
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
     * DB 저장은 saveChunk()에서 개별 트랜잭션으로 처리한다.
     */
    public void collectOneStock(BatchProgress progress) {
        StockInfo stockInfo = progress.getStockInfo();
        String symbol = stockInfo.getSymbol();
        String marketCode = resolveMarketCode(stockInfo.getMarket());

        updateStatus(progress, BatchStatus.IN_PROGRESS, null);

        try {
            LocalDate from = progress.getLastCollectedDate().plusDays(1);

            if (!from.isBefore(COLLECT_END)) {
                log.info("[이미 완료] 종목={}", symbol);
                completeProgress(progress, COLLECT_END);
                return;
            }

            int totalSaved = 0;

            while (from.isBefore(COLLECT_END) || from.isEqual(COLLECT_END)) {
                LocalDate to = from.plusDays(KIS_MAX_ROWS - 1);
                if (to.isAfter(COLLECT_END)) {
                    to = COLLECT_END;
                }

                KisCandleResponse response = kisStockClient.fetchDailyPrice(symbol, marketCode, from, to);

                if (response != null && response.output2() != null) {
                    int saved = saveChunk(stockInfo, response.output2());
                    totalSaved += saved;
                }

                from = to.plusDays(1);

                // API 호출 간 1초 딜레이
                if (from.isBefore(COLLECT_END) || from.isEqual(COLLECT_END)) {
                    Thread.sleep(API_DELAY_MS);
                }
            }

            completeProgress(progress, COLLECT_END);
            log.info("[수집 완료] 종목={}, 저장={}건", symbol, totalSaved);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failProgress(progress, "수집 중 인터럽트 발생");
            throw new RuntimeException("수집 중 인터럽트", e);
        } catch (Exception e) {
            failProgress(progress, truncateMessage(e.getMessage(), 500));
            throw e;
        }
    }

    /**
     * 시장명(KOSPI/KOSDAQ)을 KIS API 시장코드(J/Q)로 변환한다.
     */
    private String resolveMarketCode(String market) {
        if ("KOSDAQ".equalsIgnoreCase(market)) {
            return "Q";
        }
        return "J";
    }

    /**
     * KIS API 응답 1청크를 트랜잭션 단위로 저장한다.
     * 이미 존재하는 날짜는 스킵 (중복 방지).
     */
    @Transactional
    public int saveChunk(StockInfo stockInfo, List<KisCandleResponse.Output2> candles) {
        // 청크 내 모든 날짜를 한 번에 조회 (N+1 방지)
        List<LocalDate> allDates = candles.stream()
                .map(c -> LocalDate.parse(c.stck_bsop_date(), KIS_DATE_FORMAT))
                .toList();
        Set<LocalDate> existingDates = stockDailyRepository.findExistingDates(stockInfo, allDates);

        int savedCount = 0;

        for (KisCandleResponse.Output2 candle : candles) {
            LocalDate tradeDate = LocalDate.parse(candle.stck_bsop_date(), KIS_DATE_FORMAT);

            if (existingDates.contains(tradeDate)) {
                continue;
            }

            BigDecimal changeRate = parseBigDecimalOrNull(candle.prdy_ctrt());

            StockDaily daily = StockDaily.create(
                    stockInfo,
                    tradeDate,
                    new BigDecimal(candle.stck_oprc()),
                    new BigDecimal(candle.stck_hgpr()),
                    new BigDecimal(candle.stck_lwpr()),
                    new BigDecimal(candle.stck_clpr()),
                    new BigDecimal(candle.acml_vol()),
                    changeRate
            );

            stockDailyRepository.save(daily);
            savedCount++;
        }

        return savedCount;
    }

    @Transactional
    public void updateStatus(BatchProgress progress, BatchStatus status, String errorMessage) {
        if (status == BatchStatus.IN_PROGRESS) {
            progress.startProgress();
        }
        batchProgressRepository.save(progress);
    }

    @Transactional
    public void completeProgress(BatchProgress progress, LocalDate lastDate) {
        progress.complete(lastDate);
        batchProgressRepository.save(progress);
    }

    @Transactional
    public void failProgress(BatchProgress progress, String errorMessage) {
        progress.fail(errorMessage);
        batchProgressRepository.save(progress);
    }

    private BigDecimal parseBigDecimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "알 수 없는 에러";
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
