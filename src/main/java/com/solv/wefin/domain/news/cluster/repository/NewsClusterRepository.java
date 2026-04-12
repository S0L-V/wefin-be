package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NewsClusterRepository extends JpaRepository<NewsCluster, Long> {

    /**
     * 특정 상태의 클러스터 목록을 조회한다.
     */
    List<NewsCluster> findByStatus(ClusterStatus status);

    /**
     * 특정 상태이면서, 마지막 갱신 시각이 기준 시각 이전인 클러스터를 조회한다.
     */
    List<NewsCluster> findByStatusAndUpdatedAtBefore(ClusterStatus status, OffsetDateTime before);

    /**
     * ACTIVE 클러스터 중 요약 생성이 필요한 클러스터를 조회한다.
     */
    List<NewsCluster> findByStatusAndSummaryStatusIn(ClusterStatus status,
                                                     List<NewsCluster.SummaryStatus> statuses,
                                                     Pageable pageable);

    // --- 피드 목록: publishedAt 정렬 (기본) ---

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND (c.publishedAt < :cursorTime " +
            "     OR (c.publishedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedAfterCursorByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedFirstPageByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    // --- 피드 목록: updatedAt 정렬 ---

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND (c.updatedAt < :cursorTime " +
            "     OR (c.updatedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedAfterCursorByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedFirstPageByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    // --- 피드 목록: 태그 필터 (다중 tagCode IN) + publishedAt 정렬 ---

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "AND (c.publishedAt < :cursorTime " +
            "     OR (c.publishedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsAfterCursorByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsFirstPageByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            Pageable pageable);

    // --- 피드 목록: 태그 필터 (다중 tagCode IN) + updatedAt 정렬 ---

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "AND (c.updatedAt < :cursorTime " +
            "     OR (c.updatedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsAfterCursorByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsFirstPageByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            Pageable pageable);
}
