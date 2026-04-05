package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 클러스터 라이프사이클 관리 서비스
 *
 * 오래된 클러스터를 자동으로 비활성화한다.
 *
 * [비활성화 기준]
 * - 마지막 갱신(updatedAt) 이후 24시간 동안 변화가 없는 경우
 *
 * [동작]
 * - ACTIVE → INACTIVE 전환 (더 이상 최신 트렌드로 간주하지 않음)
 *
 * [데이터 정책]
 * - INACTIVE 클러스터는 피드에서 제외되지만,
 *   데이터는 삭제하지 않고 유지한다 (읽음 기록, 사용자 피드백 보존)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterLifecycleService {

    private static final int INACTIVE_HOURS = 24; // 비활성화 기준 경과 시간

    private final NewsClusterRepository newsClusterRepository;

    /**
     * 마지막 갱신 후 24시간이 지난 ACTIVE 클러스터를 비활성화한다.
     */
    @Transactional
    public void deactivateExpiredClusters() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(INACTIVE_HOURS); // 현재 시각에서 24시간 전

        List<NewsCluster> expired = newsClusterRepository
                .findByStatusAndUpdatedAtBefore(ClusterStatus.ACTIVE, cutoff); // updatedAt < cutoff인 ACTIVE 클러스터 조회

        if (expired.isEmpty()) {
            return;
        }

        // INACTIVE로 상태 변경
        for (NewsCluster cluster : expired) {
            cluster.deactivate();
        }

        log.info("클러스터 비활성화 완료 — {}건 INACTIVE 전환", expired.size());
    }
}
