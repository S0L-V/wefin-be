package com.solv.wefin.domain.news.entity;

import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_article_id")
    private Long id;

    @Column(name = "raw_news_article_id")
    private Long rawNewsArticleId;

    @Column(name = "publisher_name", nullable = false, length = 100)
    private String publisherName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "content")
    private String content;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "market_scope", length = 30)
    private String marketScope;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "dedup_key", length = 255)
    private String dedupKey;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "crawl_status", nullable = false, length = 30)
    private CrawlStatus crawlStatus = CrawlStatus.PENDING;

    @Column(name = "crawl_attempted_at")
    private LocalDateTime crawlAttemptedAt;

    @Column(name = "crawl_retry_count", nullable = false)
    private int crawlRetryCount = 0;

    @Column(name = "crawl_error_message")
    private String crawlErrorMessage;

    @Builder
    private NewsArticle(Long rawNewsArticleId, String publisherName, String title,
                        String summary, String content, String originalUrl,
                        String thumbnailUrl, LocalDateTime publishedAt,
                        String category, String marketScope, String languageCode,
                        String dedupKey, LocalDateTime collectedAt) {
        this.rawNewsArticleId = rawNewsArticleId;
        this.publisherName = publisherName;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.originalUrl = originalUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.category = category;
        this.marketScope = marketScope;
        this.languageCode = languageCode;
        this.dedupKey = dedupKey;
        this.collectedAt = collectedAt;
        this.crawlStatus = CrawlStatus.PENDING;
    }

    /**
     * 크롤링 성공 시 본문과 썸네일을 업데이트한다.
     *
     * <p>본문은 항상 최신 크롤링 결과로 덮어쓴다 (재크롤링 시 최신 본문 반영).
     * 썸네일은 최초 1회만 저장하고 이후 덮어쓰지 않는다
     * (원본 사이트의 og:image가 광고/기본 이미지로 변경되는 경우 방지).</p>
     *
     * @param crawledContent 크롤링된 본문 텍스트
     * @param thumbnailUrl og:image 썸네일 URL (없으면 null)
     */
    public void updateCrawledContent(String crawledContent, String thumbnailUrl) {
        this.content = crawledContent;
        if ((this.thumbnailUrl == null || this.thumbnailUrl.isBlank())
                && thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            this.thumbnailUrl = thumbnailUrl;
        }
        this.crawlStatus = CrawlStatus.SUCCESS;
        this.crawlAttemptedAt = LocalDateTime.now();
        this.crawlErrorMessage = null;
    }

    /**
     * 크롤링 실패를 기록한다. retryCount를 증가시키고 상태를 FAILED로 변경한다.
     *
     * @param errorMessage 실패 원인 메시지 (최대 500자)
     */
    public void markCrawlFailed(String errorMessage) {
        this.crawlRetryCount++;
        this.crawlStatus = CrawlStatus.FAILED;
        this.crawlAttemptedAt = LocalDateTime.now();
        this.crawlErrorMessage = errorMessage;
    }

    /** 크롤링 대상에서 제외한다. 상태를 SKIPPED로 변경한다. */
    public void markCrawlSkipped() {
        this.crawlStatus = CrawlStatus.SKIPPED;
        this.crawlAttemptedAt = LocalDateTime.now();
        this.crawlErrorMessage = null;
    }

    public enum CrawlStatus {
        PENDING, SUCCESS, FAILED, SKIPPED
    }

    public static NewsArticle of(RawNewsArticle rawArticle, CollectedNewsDto dto,
                                 String title, String content, String languageCode,
                                 String marketScope, String dedupKey) {
        return NewsArticle.builder()
                .rawNewsArticleId(rawArticle.getId())
                .publisherName(dto.getPublisherName())
                .title(title)
                .content(content)
                .originalUrl(dto.getOriginalUrl())
                .thumbnailUrl(dto.getOriginalThumbnailUrl())
                .publishedAt(dto.getOriginalPublishedAt())
                .languageCode(languageCode)
                .marketScope(marketScope)
                .dedupKey(dedupKey)
                .collectedAt(rawArticle.getCollectedAt())
                .build();
    }
}
