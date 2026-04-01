package com.solv.wefin.domain.news.tagging.batch;

import com.solv.wefin.domain.news.tagging.service.TaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 태깅 생성 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaggingScheduler {

    private final TaggingService taggingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${tagging.collect.cron:0 */30 * * * *}")
    public void generateTags() {
        execute();
    }

    /**
     * 태깅 생성을 실행한다
     *
     * @return 실행 여부 (이미 실행 중이면 false)
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.info("태깅 생성이 이미 실행 중입니다. 스킵합니다.");
            return false;
        }

        log.info("=== 태깅 생성 배치 시작 ===");
        long start = System.currentTimeMillis();

        try {
            taggingService.tagPendingArticles();
            return true;
        } catch (Exception e) {
            log.error("태깅 생성 실패: {}", e.getMessage(), e);
            return false;
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 태깅 생성 배치 종료 ({}ms) ===", elapsed);
        }
    }
}
