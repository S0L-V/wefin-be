package com.solv.wefin.domain.news.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_collect_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsCollectBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_collect_batch_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_source_id", nullable = false)
    private NewsSource newsSource;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchStatus status;

    @Column(name = "requested_category", length = 50)
    private String requestedCategory;

    @Column(name = "collected_count", nullable = false)
    private int collectedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Builder
    public NewsCollectBatch(NewsSource newsSource, String requestedCategory) {
        this.newsSource = newsSource;
        this.requestedCategory = requestedCategory;
        this.startedAt = LocalDateTime.now();
        this.status = BatchStatus.RUNNING;
        this.collectedCount = 0;
        this.failedCount = 0;
    }

    public void success(int collectedCount, int failedCount) {
        this.status = BatchStatus.SUCCESS;
        this.collectedCount = collectedCount;
        this.failedCount = failedCount;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage, int collectedCount, int failedCount) {
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.collectedCount = collectedCount;
        this.failedCount = failedCount;
        this.finishedAt = LocalDateTime.now();
    }

    public enum BatchStatus {
        READY, RUNNING, SUCCESS, FAILED
    }
}
