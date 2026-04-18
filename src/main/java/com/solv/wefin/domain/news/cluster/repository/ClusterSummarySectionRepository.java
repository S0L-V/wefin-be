package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClusterSummarySectionRepository extends JpaRepository<ClusterSummarySection, Long> {

    /**
     * 특정 클러스터의 요약 섹션을 순서대로 조회한다
     *
     * @param newsClusterId 클러스터 ID
     * @return 섹션 목록 (section_order 오름차순)
     */
    List<ClusterSummarySection> findByNewsClusterIdOrderBySectionOrderAsc(Long newsClusterId);

    /**
     * 특정 클러스터의 섹션 출처 매핑을 모두 삭제한다.
     * 섹션 삭제 전에 먼저 호출하여 FK 위반을 방지한다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ClusterSummarySectionSource s WHERE s.clusterSummarySectionId IN " +
            "(SELECT cs.id FROM ClusterSummarySection cs WHERE cs.newsClusterId = :clusterId)")
    void deleteSourcesByNewsClusterId(@Param("clusterId") Long clusterId);

    /**
     * 특정 클러스터의 기존 섹션을 모두 삭제한다.
     * STALE 재생성 시 기존 섹션을 정리한 뒤 새로 생성하기 위해 사용한다.
     * 반드시 deleteSourcesByNewsClusterId를 먼저 호출할 것
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ClusterSummarySection cs WHERE cs.newsClusterId = :clusterId")
    void deleteByNewsClusterId(@Param("clusterId") Long clusterId);
}
