package com.solv.wefin.domain.news.article.repository;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByDedupKey(String dedupKey);

    /**
     * 출처 표시용 — 엔티티 전체가 아닌 publisherName + originalUrl만 조회한다.
     * content(TEXT) 등 불필요한 대용량 컬럼 로딩을 방지.
     */
    @Query("SELECT a.id AS id, a.publisherName AS publisherName, a.originalUrl AS originalUrl " +
            "FROM NewsArticle a WHERE a.id IN :ids")
    List<SourceProjection> findSourceInfoByIdIn(@Param("ids") List<Long> ids);

    interface SourceProjection {
        Long getId();
        String getPublisherName();
        String getOriginalUrl();
    }

    /**
     * 재판정 가능한 PENDING 기사를 id 오름차순으로 조회한다.
     */
    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.relevance = :relevance " +
            "AND a.content IS NOT NULL AND TRIM(a.content) <> '' " +
            "ORDER BY a.id ASC")
    List<NewsArticle> findRejudgeTargets(
            @Param("relevance") NewsArticle.RelevanceStatus relevance,
            Pageable pageable);

    List<NewsArticle> findByCrawlStatusInAndCrawlRetryCountLessThanOrderByCollectedAtDesc(
            List<NewsArticle.CrawlStatus> statuses, int maxRetryCount, Pageable pageable);

    /**
     * 임베딩 대상 기사를 조회한다.
     */
    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.crawlStatus = :crawlStatus " +
            "AND a.relevance <> :excludedRelevance " +
            "AND a.embeddingRetryCount < :maxRetryCount " +
            "AND (a.embeddingStatus IN :embeddingStatuses " +
            "     OR (a.embeddingStatus = :processingStatus AND a.embeddingAttemptedAt < :staleBefore)) " +
            "ORDER BY a.collectedAt DESC")
    List<NewsArticle> findEmbeddingTargets(
            @Param("crawlStatus") NewsArticle.CrawlStatus crawlStatus,
            @Param("embeddingStatuses") List<NewsArticle.EmbeddingStatus> embeddingStatuses,
            @Param("processingStatus") NewsArticle.EmbeddingStatus processingStatus,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("staleBefore") OffsetDateTime staleBefore,
            @Param("excludedRelevance") NewsArticle.RelevanceStatus excludedRelevance,
            Pageable pageable);

    /**
     * 태깅 대상 기사를 조회한다.
     * PENDING/FAILED 상태이거나, PROCESSING 상태에서 staleBefore 이전에 시도된 기사를 포함한다.
     */
    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.crawlStatus = :crawlStatus " +
            "AND a.taggingRetryCount < :maxRetryCount " +
            "AND (a.taggingStatus IN :taggingStatuses " +
            "     OR (a.taggingStatus = :processingStatus AND a.taggingAttemptedAt < :staleBefore)) " +
            "ORDER BY a.collectedAt DESC")
    List<NewsArticle> findTaggingTargets(
            @Param("crawlStatus") NewsArticle.CrawlStatus crawlStatus,
            @Param("taggingStatuses") List<NewsArticle.TaggingStatus> taggingStatuses,
            @Param("processingStatus") NewsArticle.TaggingStatus processingStatus,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("staleBefore") OffsetDateTime staleBefore,
            Pageable pageable);

    /**
     * 클러스터링 대상 기사를 조회한다.
     */
    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.embeddingStatus = :embeddingStatus " +
            "AND a.relevance <> :excludedRelevance " +
            "AND NOT EXISTS (SELECT 1 FROM NewsClusterArticle nca WHERE nca.newsArticleId = a.id) " +
            "AND a.createdAt > :since " +
            "ORDER BY a.collectedAt DESC")
    List<NewsArticle> findClusteringTargets(
            @Param("embeddingStatus") NewsArticle.EmbeddingStatus embeddingStatus,
            @Param("since") OffsetDateTime since,
            @Param("excludedRelevance") NewsArticle.RelevanceStatus excludedRelevance,
            Pageable pageable);
}
