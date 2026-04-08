package com.solv.wefin.domain.news.cluster.batch;

import com.solv.wefin.domain.news.cluster.service.ClusterMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 클러스터 병합 배치 스케줄러.
 * 6시간 간격으로 유사한 ACTIVE 클러스터를 병합하여 중복을 해소한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMergeScheduler {

    private final ClusterMergeService clusterMergeService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${clustering.merge.cron:0 0 */6 * * *}")
    public void mergeScheduled() {
        log.info("클러스터 병합 배치 시작");
        try {
            boolean executed = execute();
            if (executed) {
                log.info("클러스터 병합 배치 완료");
            }
        } catch (Exception e) {
            log.error("클러스터 병합 배치 실패", e);
        }
    }

    /**
     * 수동 트리거 또는 스케줄러에서 호출한다.
     *
     * @return true면 실행됨, false면 이미 실행 중
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.warn("클러스터 병합 이미 실행 중 — 스킵");
            return false;
        }
        try {
            clusterMergeService.mergeActiveClusters();
            return true;
        } finally {
            running.set(false);
        }
    }
}
