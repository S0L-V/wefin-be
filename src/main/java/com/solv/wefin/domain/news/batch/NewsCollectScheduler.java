package com.solv.wefin.domain.news.batch;

import com.solv.wefin.domain.news.service.NewsCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCollectScheduler {

    private final NewsCollectService newsCollectService;

    @Scheduled(cron = "${news.collect.cron:0 0 */2 * * *}")
    public void collectNews() {
        log.info("=== 뉴스 수집 배치 시작 ===");
        long start = System.currentTimeMillis();

        try {
            newsCollectService.collectAll();
        } catch (Exception e) {
            log.error("뉴스 수집 배치 실패: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("=== 뉴스 수집 배치 종료 ({}ms) ===", elapsed);
    }
}
