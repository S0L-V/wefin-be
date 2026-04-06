package com.solv.wefin.domain.news.summary.batch;

import com.solv.wefin.domain.news.summary.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 요약 생성 배치 스케줄러
 *
 * 30분 간격으로 ACTIVE 클러스터 중 요약 생성이 필요한(PENDING/STALE/FAILED) 클러스터의 요약을 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryScheduler {

    private final SummaryService summaryService;
    private final AtomicBoolean running = new AtomicBoolean(false); // 중복 실행 방지

    @Scheduled(cron = "${summary.collect.cron:0 */30 * * * *}")
    public void generateSummaries() {
        try {
            execute();
        } catch (Exception e) {
            log.error("요약 생성 배치 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 요약 생성을 실행한다.
     *
     * @return 이미 실행 중이면 false, 정상 실행되면 true
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.info("요약 생성이 이미 실행 중입니다. 스킵합니다.");
            return false;
        }

        log.info("=== 요약 생성 배치 시작 ===");
        long start = System.currentTimeMillis();

        try {
            summaryService.generatePendingSummaries();
            return true;
        } catch (Exception e) {
            log.error("요약 생성 실행 실패: {}", e.getMessage(), e);
            throw e;
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 요약 생성 배치 종료 ({}ms) ===", elapsed);
        }
    }
}
