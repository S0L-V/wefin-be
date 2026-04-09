package com.solv.wefin.domain.news.article.repository;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleTagRepository extends JpaRepository<NewsArticleTag, Long> {

    /**
     * 피드에 노출 가능한 클러스터에 속한 기사의 인기 태그를 조회한다.
     * 클러스터 수 기준 내림차순 정렬. Pageable로 limit 제어
     */
    @Query("SELECT t.tagCode AS tagCode, t.tagName AS tagName, COUNT(DISTINCT nc.id) AS clusterCount " +
            "FROM NewsArticleTag t " +
            "JOIN NewsClusterArticle nca ON nca.newsArticleId = t.newsArticleId " +
            "JOIN NewsCluster nc ON nc.id = nca.newsClusterId " +
            "WHERE t.tagType = :tagType " +
            "AND nc.status = :status " +
            "AND nc.summaryStatus IN :summaryStatuses " +
            "AND nc.title IS NOT NULL " +
            "GROUP BY t.tagCode, t.tagName " +
            "ORDER BY COUNT(DISTINCT nc.id) DESC")
    List<PopularTagProjection> findPopularTags(
            @Param("tagType") NewsArticleTag.TagType tagType,
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    interface PopularTagProjection {
        String getTagCode();
        String getTagName();
        Long getClusterCount();
    }

    List<NewsArticleTag> findByNewsArticleId(Long newsArticleId);

    /**
     * 여러 기사의 태그를 한 번에 조회한다.
     */
    List<NewsArticleTag> findByNewsArticleIdIn(List<Long> newsArticleIds);

    /**
     * 여러 기사의 특정 타입 태그만 조회한다.
     */
    List<NewsArticleTag> findByNewsArticleIdInAndTagType(List<Long> newsArticleIds, NewsArticleTag.TagType tagType);

    /**
     * 특정 기사의 태그를 전부 삭제한다. 재태깅 시 기존 태그 정리에 사용한다.
     */
    void deleteByNewsArticleId(Long newsArticleId);
}
