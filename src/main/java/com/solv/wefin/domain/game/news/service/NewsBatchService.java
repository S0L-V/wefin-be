package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsBatchService {

    private static final LocalDate COLLECT_START = LocalDate.of(2020, 1, 2);
    private static final LocalDate COLLECT_END = LocalDate.of(2024, 12, 31);
    private static final int BATCH_SIZE = 150;
    private static final long OPENAI_DELAY_MS = 1_000;

    private final BriefingService briefingService;
    private final BriefingCacheRepository briefingCacheRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 비동기로 배치를 실행한다. Controller에서 즉시 응답 반환용.
     */
    @Async
    public void collectBatchAsync(int days) {
        collectBatch(days);
    }

    /**
     * 다음 배치 분량의 뉴스 크롤링 + 브리핑 생성을 수행한다.
     * 이미 briefing_cache에 있는 날짜는 건너뛴다.
     *
     * @param days 처리할 날짜 수 (기본 150)
     * @return 신규 처리한 날짜 수
     */
    public int collectBatch(int days) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[뉴스 배치 스킵] 이미 수집이 진행 중입니다");
            return 0;
        }

        try {
            return doCollectBatch(days);
        } finally {
            running.set(false);
        }
    }

    private int doCollectBatch(int days) {
        // 1. 이미 처리된 날짜를 BETWEEN으로 조회 (IN절 1,826개 방지)
        Set<LocalDate> existingDates = briefingCacheRepository
                .findExistingDatesBetween(COLLECT_START, COLLECT_END);

        // 2. 미처리 날짜 목록 생성
        List<LocalDate> targetDates = new ArrayList<>();
        LocalDate current = COLLECT_START;
        while (!current.isAfter(COLLECT_END) && targetDates.size() < days) {
            if (!existingDates.contains(current)) {
                targetDates.add(current);
            }
            current = current.plusDays(1);
        }

        long totalDays = COLLECT_START.until(COLLECT_END).getDays() + 1;

        if (targetDates.isEmpty()) {
            log.info("[뉴스 배치] 모든 날짜 처리 완료 ({}/{})",
                    existingDates.size(), totalDays);
            return 0;
        }

        log.info("[뉴스 배치] 시작: 미처리 {}건 중 {}건 처리 예정 (전체 진행: {}/{})",
                totalDays - existingDates.size(), targetDates.size(),
                existingDates.size(), totalDays);

        // 3. 배치 처리
        int processed = 0;
        int failed = 0;

        for (LocalDate date : targetDates) {
            try {
                briefingService.getBriefingForDate(date);
                processed++;

                if (processed % 10 == 0) {
                    log.info("[뉴스 배치] 진행: {}/{}, 현재 날짜={}", processed, targetDates.size(), date);
                }

                Thread.sleep(OPENAI_DELAY_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[뉴스 배치 중단] 인터럽트 발생, 처리 완료={}건", processed);
                break;
            } catch (Exception e) {
                failed++;
                log.error("[뉴스 배치 실패] date={}, error={}", date, e.getMessage());
            }
        }

        log.info("[뉴스 배치 완료] 신규={}건, 실패={}건, 전체 진행={}/{}",
                processed, failed, existingDates.size() + processed, totalDays);
        return processed;
    }
}
