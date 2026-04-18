package com.solv.wefin.domain.market.trend.scheduler;

import com.solv.wefin.domain.market.trend.service.MarketTrendGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 오늘의 금융 동향 생성 배치 스케줄러
 *
 * 기본 30분 간격. 환경변수 {@code market.trend.cron}로 조정 가능.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTrendScheduler {

    private final MarketTrendGenerationService generationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${market.trend.cron:0 */30 * * * *}")
    public void generateMarketTrend() {
        try {
            execute();
        } catch (Exception e) {
            log.error("금융 동향 생성 배치 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 수동 트리거에서도 호출 가능하도록 public.
     * 이미 실행 중이면 false 반환
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.info("금융 동향 생성이 이미 실행 중입니다. 스킵합니다.");
            return false;
        }

        log.info("=== 금융 동향 생성 배치 시작 ===");
        long start = System.currentTimeMillis();
        try {
            generationService.generateTodayTrend();
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 금융 동향 생성 배치 종료 ({}ms) ===", elapsed);
        }
        return true;
    }
}
