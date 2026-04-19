package com.solv.wefin.domain.news.recommendation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자별 추천 뉴스 카드 — 캐시 + 이력 이중 역할을 수행한다
 */
@Entity
@Table(name = "recommended_news_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendedNewsCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 10)
    private CardType cardType;

    @Column(name = "interest_code", nullable = false, length = 50)
    private String interestCode;

    @Column(name = "interest_name", nullable = false, length = 100)
    private String interestName;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "context", nullable = false, columnDefinition = "TEXT")
    private String context;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb")
    private String reasons;

    @Column(name = "linked_cluster_id", nullable = false)
    private Long linkedClusterId;

    @Column(name = "interest_hash", nullable = false, length = 64)
    private String interestHash;

    @Column(name = "session_started_at", nullable = false)
    private OffsetDateTime sessionStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum CardType {
        STOCK, SECTOR
    }

    @Builder
    private RecommendedNewsCard(UUID userId, CardType cardType,
                                String interestCode, String interestName,
                                String title, String summary, String context,
                                String reasons, Long linkedClusterId,
                                String interestHash, OffsetDateTime sessionStartedAt) {
        this.userId = userId;
        this.cardType = cardType;
        this.interestCode = interestCode;
        this.interestName = interestName;
        this.title = title;
        this.summary = summary;
        this.context = context;
        this.reasons = reasons;
        this.linkedClusterId = linkedClusterId;
        this.interestHash = interestHash;
        this.sessionStartedAt = sessionStartedAt;
        this.createdAt = OffsetDateTime.now();
    }
}
