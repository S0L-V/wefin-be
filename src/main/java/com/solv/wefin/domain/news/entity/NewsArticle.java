package com.solv.wefin.domain.news.entity;

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

    @Builder
    public NewsArticle(Long rawNewsArticleId, String publisherName, String title,
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
    }
}
