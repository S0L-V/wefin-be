package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByDedupKey(String dedupKey);

    List<NewsArticle> findByCrawlStatusInAndCrawlRetryCountLessThanOrderByCollectedAtDesc(
            List<NewsArticle.CrawlStatus> statuses, int maxRetryCount, Pageable pageable);

    @Query("SELECT a FROM NewsArticle a " +
            "WHERE a.crawlStatus = :crawlStatus " +
            "AND a.embeddingStatus IN :embeddingStatuses " +
            "AND a.embeddingRetryCount < :maxRetryCount " +
            "ORDER BY a.collectedAt DESC")
    List<NewsArticle> findEmbeddingTargets(
            @Param("crawlStatus") NewsArticle.CrawlStatus crawlStatus,
            @Param("embeddingStatuses") List<NewsArticle.EmbeddingStatus> embeddingStatuses,
            @Param("maxRetryCount") int maxRetryCount,
            Pageable pageable);
}
