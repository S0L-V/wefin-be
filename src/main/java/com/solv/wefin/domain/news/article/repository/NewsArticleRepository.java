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

    List<NewsArticle> findByCrawlStatusInAndCrawlRetryCountLessThanOrderByCollectedAtDesc(
            List<NewsArticle.CrawlStatus> statuses, int maxRetryCount, Pageable pageable);

    /**
     * 임베딩 대상 기사를 조회한다.
     * PENDING/FAILED 상태이거나, PROCESSING 상태에서 staleBefore 이전에 시도된 기사를 포함한다.
     */
    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.crawlStatus = :crawlStatus " +
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
}
