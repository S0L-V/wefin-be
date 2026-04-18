package com.solv.wefin.domain.market.trend.entity;

import com.solv.wefin.domain.market.trend.dto.PersonalizationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자별 맞춤 금융 동향 캐시 (당일 1회 AI 호출 결과 보관용)
 *
 * {@code (user_id, trend_date)} 기준으로 unique하며, 다음 날부터는 자동 fresh.
 * 관심사 변경(추가/삭제) 시 해당 사용자의 오늘자 row를 명시적으로 삭제해 무효화한다
 */
@Entity
@Table(
        name = "user_market_trend",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_market_trend_user_date",
                columnNames = {"user_id", "trend_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMarketTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_market_trend_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trend_date", nullable = false)
    private LocalDate trendDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 30)
    private PersonalizationMode mode;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "insight_cards", nullable = false, columnDefinition = "jsonb")
    private String insightCardsJson;

    @Column(name = "related_keywords", nullable = false, columnDefinition = "jsonb")
    private String relatedKeywordsJson;

    @Column(name = "source_cluster_ids", nullable = false, columnDefinition = "jsonb")
    private String sourceClusterIdsJson;

    @Column(name = "source_article_count", nullable = false)
    private int sourceArticleCount;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
