package com.solv.wefin.domain.game.batch.scheduler;

import com.solv.wefin.domain.game.batch.service.StockCollectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "stock.collect.enabled", havingValue = "true")
public class StockCollectScheduler {

    private final StockCollectService stockCollectService;
    private final int batchSize;

    public StockCollectScheduler(
            StockCollectService stockCollectService,
            @Value("${stock.collect.batch-size:320}") int batchSize) {
        this.stockCollectService = stockCollectService;
        this.batchSize = batchSize;
    }

    /**
     * 하루 5회 × 320종목 = 1,600종목/일 → 2일이면 전 종목 완료
     */
    @Scheduled(cron = "0 0 13 * * *")
    public void collectAt1300() {
        runCollect("13:00");
    }

    @Scheduled(cron = "0 0 16 * * *")
    public void collectAt1600() {
        runCollect("16:00");
    }

    @Scheduled(cron = "0 0 20 * * *")
    public void collectAt2000() {
        runCollect("20:00");
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void collectAt2300() {
        runCollect("23:00");
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void collectAt0230() {
        runCollect("02:30");
    }

    private void runCollect(String scheduleLabel) {
        log.info("[주가 수집 시작] 스케줄={}, 배치크기={}", scheduleLabel, batchSize);
        try {
            int successCount = stockCollectService.collectBatch(batchSize);
            log.info("[주가 수집 종료] 스케줄={}, 성공={}종목", scheduleLabel, successCount);
        } catch (Exception e) {
            log.error("[주가 수집 에러] 스케줄={}", scheduleLabel, e);
        }
    }
}
