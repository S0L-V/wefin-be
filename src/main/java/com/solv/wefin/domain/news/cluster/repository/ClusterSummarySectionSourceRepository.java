package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSourceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusterSummarySectionSourceRepository extends JpaRepository<ClusterSummarySectionSource, ClusterSummarySectionSourceId> {

    /**
     * 특정 섹션의 근거 기사 매핑을 조회한다
     *
     * @param clusterSummarySectionId 섹션 ID
     * @return 출처 매핑 목록
     */
    List<ClusterSummarySectionSource> findByClusterSummarySectionId(Long clusterSummarySectionId);

    /**
     * 여러 섹션의 근거 기사 매핑을 일괄 조회한다
     *
     * @param sectionIds 섹션 ID 목록
     * @return 출처 매핑 목록
     */
    List<ClusterSummarySectionSource> findByClusterSummarySectionIdIn(List<Long> sectionIds);
}
