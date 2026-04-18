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
     * 하루 4회 뉴스 크롤링 + AI 브리핑 생성 (150일치 × 4 = 600일/day).
     * 약 3일이면 전체 기간(2020~2024, 1,825일) 완료.
     * 수집 완료 후에는 이미 처리된 날짜를 건너뛰므로 추가 부하 없음.
     */
    @Scheduled(cron = "0 43 4 * * *", zone = "Asia/Seoul")
    public void collectMorning() {
        runBatch("04:00");
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
    public void collectNoon() {
        runBatch("12:00");
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void collectEvening() {
        runBatch("19:00");
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void collectMidnight() {
        runBatch("00:00");
    }

    private void runBatch(String schedule) {
        log.info("[뉴스 배치 시작] 스케줄={}", schedule);
        try {
            int count = newsBatchService.collectBatch(150);
            log.info("[뉴스 배치 종료] 스케줄={}, 처리={}건", schedule, count);
        } catch (Exception e) {
            log.error("[뉴스 배치 에러] 스케줄={}", schedule, e);
        }
    }
}
