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
     * 하루 4회 × 320종목 = 1,280종목/일 → 약 2일이면 전 종목 완료
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void collectAt0142() {
        runCollect("01:42");
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void collectAt0930() {
        runCollect("09:30");
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void collectAt1300() {
        runCollect("13:00");
    }

    @Scheduled(cron = "0 0 18 * * *")
    public void collectAt1800() {
        runCollect("18:00");
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
