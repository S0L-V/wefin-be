package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.ClusterSuggestedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 클러스터 추천 질문 Repository
 *
 * 상세 API 조회 시 질문 목록 반환, STALE 재생성 시 기존 질문 삭제에 사용된다
 */
public interface ClusterSuggestedQuestionRepository extends JpaRepository<ClusterSuggestedQuestion, Long> {

    /**
     * 클러스터의 추천 질문을 순서대로 조회한다
     */
    List<ClusterSuggestedQuestion> findByNewsClusterIdOrderByQuestionOrder(Long newsClusterId);

    /**
     * 클러스터의 기존 추천 질문을 모두 삭제한다
     *
     * STALE 재생성 시 기존 질문을 삭제한 뒤 새로 생성하기 위해 사용된다
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ClusterSuggestedQuestion q WHERE q.newsClusterId = :newsClusterId")
    void deleteByNewsClusterId(@Param("newsClusterId") Long newsClusterId);
}
