package com.solv.wefin.domain.market.batch;

import com.solv.wefin.domain.market.service.MarketSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 시장 지표 수집 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSnapshotScheduler {

    private final MarketSnapshotService marketSnapshotService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${market.collect.cron:0 */5 * * * *}")
    public void collectMarketData() {
        if (isWeekend()) {
            log.debug("주말이므로 시장 지표 수집을 스킵합니다.");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.info("시장 지표 수집이 이미 실행 중입니다. 스킵합니다.");
            return;
        }

        log.info("=== 시장 지표 수집 시작 ===");
        long start = System.currentTimeMillis();

        try {
            marketSnapshotService.collectAndSave();
        } catch (Exception e) {
            log.error("시장 지표 수집 실패: {}", e.getMessage(), e);
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 시장 지표 수집 종료 ({}ms) ===", elapsed);
        }
    }

    private boolean isWeekend() {
        DayOfWeek day = LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
