package com.solv.wefin.domain.news.cluster.batch;

import com.solv.wefin.domain.news.cluster.service.ClusterLifecycleService;
import com.solv.wefin.domain.news.cluster.service.ClusteringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 클러스터링 배치 스케줄러
 *
 * 30분 간격으로 미배정 기사를 클러스터링한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusteringScheduler {

    private final ClusteringService clusteringService;
    private final ClusterLifecycleService clusterLifecycleService;
    private final AtomicBoolean running = new AtomicBoolean(false); // 현재 배치 실행 여부 플래그 (중복 실행 방지)

    @Scheduled(cron = "${clustering.collect.cron:0 */30 * * * *}")
    public void clusterArticles() {
        try {
            execute();
        } catch (Exception e) {
            log.error("클러스터링 배치 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 클러스터링을 실행한다.
     *
     * @return 이미 실행 중이면 false, 정상 실행되면 true
     * @throws RuntimeException 클러스터링 실행 중 예외 발생 시
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.info("클러스터링이 이미 실행 중입니다. 스킵합니다.");
            return false;
        }

        log.info("=== 클러스터링 배치 시작 ===");
        long start = System.currentTimeMillis();

        try {
            // 1. 24시간 지난 클러스터 비활성화 (실패해도 클러스터링은 계속)
            try {
                clusterLifecycleService.deactivateExpiredClusters();
            } catch (Exception e) {
                log.error("클러스터 비활성화 실패 (클러스터링은 계속 진행): {}", e.getMessage(), e);
            }
            // 2. 미배정 기사 클러스터링
            clusteringService.clusterPendingArticles();
            return true;
        } catch (Exception e) {
            log.error("클러스터링 실행 실패: {}", e.getMessage(), e);
            throw e; // AdminController 경로에서도 로그 보장
        } finally {
            running.set(false); // 배치 종료 시 락 해제
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 클러스터링 배치 종료 ({}ms) ===", elapsed);
        }
    }
}
