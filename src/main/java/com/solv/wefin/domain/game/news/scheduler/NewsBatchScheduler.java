package com.solv.wefin.domain.game.news.scheduler;

import com.solv.wefin.domain.game.news.service.NewsBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "briefing.collect.enabled", havingValue = "true")
public class NewsBatchScheduler {

    private final NewsBatchService newsBatchService;

    /**
     * 매일 04:00에 150일치 뉴스 크롤링 + AI 브리핑 생성.
     * 약 20분 소요 (크롤링 10분 + OpenAI 10분).
     * 9일이면 전체 기간(2020~2024) 완료.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void collectDaily() {
        log.info("[뉴스 배치 시작] 스케줄=04:00");
        try {
            int count = newsBatchService.collectBatch(150);
            log.info("[뉴스 배치 종료] 스케줄=04:00, 처리={}건", count);
        } catch (Exception e) {
            log.error("[뉴스 배치 에러] 스케줄=04:00", e);
        }
    }
}
