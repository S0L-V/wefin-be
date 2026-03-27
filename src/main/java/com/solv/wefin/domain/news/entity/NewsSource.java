package com.solv.wefin.domain.news.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_source_id")
    private Long id;

    @Column(name = "source_name", nullable = false, length = 100, unique = true)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public NewsSource(String sourceName, SourceType sourceType, String baseUrl, Boolean isActive) {
        this.sourceName = sourceName;
        this.sourceType = sourceType;
        this.baseUrl = baseUrl;
        this.isActive = isActive;
    }

    public enum SourceType {
        API, RSS, CRAWLER
    }
}
