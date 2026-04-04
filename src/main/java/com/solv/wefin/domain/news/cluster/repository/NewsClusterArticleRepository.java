package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsClusterArticleRepository extends JpaRepository<NewsClusterArticle, Long> {

    /**
     * 특정 클러스터의 소속 기사 매핑을 조회한다.
     */
    List<NewsClusterArticle> findByNewsClusterId(Long newsClusterId);

    /**
     * 특정 클러스터의 소속 기사 매핑을 최신순으로 조회한다.
     */
    List<NewsClusterArticle> findByNewsClusterIdOrderByCreatedAtDesc(Long newsClusterId, Pageable pageable);

    /**
     * 특정 클러스터의 소속 기사 수를 조회한다.
     */
    int countByNewsClusterId(Long newsClusterId);

    /**
     * 특정 기사가 클러스터에 이미 배정되었는지 확인한다.
     */
    boolean existsByNewsArticleId(Long newsArticleId);

    /**
     * 특정 클러스터에서 특정 기사 매핑 1건을 삭제한다. 이상치 제거 시 사용.
     *
     * @return 삭제된 건수 (정상이면 1, 매핑이 없었으면 0)
     */
    long deleteByNewsClusterIdAndNewsArticleId(Long newsClusterId, Long newsArticleId);
}
