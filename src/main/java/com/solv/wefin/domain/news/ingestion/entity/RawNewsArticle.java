package com.solv.wefin.domain.news.ingestion.entity;

import com.solv.wefin.domain.news.source.entity.NewsSource;
import com.solv.wefin.domain.news.ingestion.client.dto.CollectedNewsApiResponse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "raw_news_article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "raw_news_article_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_source_id", nullable = false)
    private NewsSource newsSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_collect_batch_id", nullable = false)
    private NewsCollectBatch newsCollectBatch;

    @Column(name = "external_article_id", length = 255)
    private String externalArticleId;

    @Column(name = "original_url", nullable = false, unique = true)
    private String originalUrl;

    @Column(name = "original_title", nullable = false)
    private String originalTitle;

    @Column(name = "original_content")
    private String originalContent;

    @Column(name = "original_thumbnail_url")
    private String originalThumbnailUrl;

    @Column(name = "original_published_at")
    private LocalDateTime originalPublishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private ProcessingStatus processingStatus;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    private RawNewsArticle(NewsSource newsSource, NewsCollectBatch newsCollectBatch,
                           String externalArticleId, String originalUrl, String originalTitle,
                           String originalContent, String originalThumbnailUrl,
                           LocalDateTime originalPublishedAt, String rawPayload) {
        this.newsSource = newsSource;
        this.newsCollectBatch = newsCollectBatch;
        this.externalArticleId = externalArticleId;
        this.originalUrl = originalUrl;
        this.originalTitle = originalTitle;
        this.originalContent = originalContent;
        this.originalThumbnailUrl = originalThumbnailUrl;
        this.originalPublishedAt = originalPublishedAt;
        this.rawPayload = rawPayload;
        this.processingStatus = ProcessingStatus.PENDING;
        this.collectedAt = LocalDateTime.now();
    }

    public static RawNewsArticle of(CollectedNewsApiResponse dto, NewsSource source, NewsCollectBatch batch) {
        return RawNewsArticle.builder()
                .newsSource(source)
                .newsCollectBatch(batch)
                .externalArticleId(dto.getExternalArticleId())
                .originalUrl(dto.getOriginalUrl())
                .originalTitle(dto.getOriginalTitle())
                .originalContent(dto.getOriginalContent())
                .originalThumbnailUrl(dto.getOriginalThumbnailUrl())
                .originalPublishedAt(dto.getOriginalPublishedAt())
                .rawPayload(dto.getRawPayload())
                .build();
    }

    public void markNormalized() {
        this.processingStatus = ProcessingStatus.NORMALIZED;
    }

    public void markFailed() {
        this.processingStatus = ProcessingStatus.FAILED;
    }

    public void markDuplicated() {
        this.processingStatus = ProcessingStatus.DUPLICATED;
    }

    public enum ProcessingStatus {
        PENDING, NORMALIZED, FAILED, DUPLICATED
    }
}
