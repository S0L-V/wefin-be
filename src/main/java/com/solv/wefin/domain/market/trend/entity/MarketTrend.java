package com.solv.wefin.domain.market.trend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 오늘의 금융 동향 엔티티 (AI 생성 요약 + 인사이트 카드 + 키워드)
 *
 * 시장 지표(MarketSnapshot)와 최근 24시간 클러스터 태그 집계를 종합하여 AI가 생성한다.
 * (trend_date, session) 기준으로 upsert되며 현재는 session='DAILY'로 고정하여
 * 하루 1건만 최신화한다. 조회 API에서는 최신 1건을 가져와 MarketSnapshot과 함께 응답한다.
 */
@Entity
@Table(name = "market_trend")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketTrend {

    public static final String SESSION_DAILY = "DAILY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_trend_id")
    private Long id;

    @Column(name = "trend_date", nullable = false)
    private LocalDate trendDate;

    @Column(name = "session", nullable = false, length = 20)
    private String session;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * 인사이트 카드 배열. {@code [{"headline": "...", "body": "...", "relatedClusterIds": [1, 3]}]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insight_cards", columnDefinition = "jsonb")
    private String insightCardsJson;

    /**
     * 떠오르는 키워드 배열 (JSON 문자열). {@code ["반도체", "엔비디아", "금리"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_keywords", columnDefinition = "jsonb")
    private String relatedKeywordsJson;

    /**
     * 동향 생성에 사용된 클러스터 ID 목록 (JSON 배열 문자열). {@code [12, 14, 18]}.
     * 조회 시점에 news_cluster를 JOIN하여 title/publishedAt을 함께 노출한다
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_cluster_ids", columnDefinition = "jsonb")
    private String sourceClusterIdsJson;

    /** 동향 생성에 사용된 기사 총 개수 (사용 클러스터 소속 기사 수의 합) */
    @Column(name = "source_article_count")
    private Integer sourceArticleCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private MarketTrend(LocalDate trendDate, String session, String title, String summary,
                        String insightCardsJson, String relatedKeywordsJson,
                        String sourceClusterIdsJson, Integer sourceArticleCount) {
        this.trendDate = trendDate;
        this.session = session;
        this.title = title;
        this.summary = summary;
        this.insightCardsJson = insightCardsJson;
        this.relatedKeywordsJson = relatedKeywordsJson;
        this.sourceClusterIdsJson = sourceClusterIdsJson;
        this.sourceArticleCount = sourceArticleCount;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 새 오늘 동향을 생성한다 (session={@link #SESSION_DAILY} 고정)
     */
    public static MarketTrend createDaily(LocalDate trendDate, String title, String summary,
                                          String insightCardsJson, String relatedKeywordsJson,
                                          String sourceClusterIdsJson, Integer sourceArticleCount) {
        return MarketTrend.builder()
                .trendDate(trendDate)
                .session(SESSION_DAILY)
                .title(title)
                .summary(summary)
                .insightCardsJson(insightCardsJson)
                .relatedKeywordsJson(relatedKeywordsJson)
                .sourceClusterIdsJson(sourceClusterIdsJson)
                .sourceArticleCount(sourceArticleCount)
                .build();
    }

    /**
     * 기존 row의 콘텐츠를 최신 AI 생성 결과로 교체한다 (같은 날 재생성 대응)
     */
    public void updateContent(String title, String summary,
                              String insightCardsJson, String relatedKeywordsJson,
                              String sourceClusterIdsJson, Integer sourceArticleCount) {
        this.title = title;
        this.summary = summary;
        this.insightCardsJson = insightCardsJson;
        this.relatedKeywordsJson = relatedKeywordsJson;
        this.sourceClusterIdsJson = sourceClusterIdsJson;
        this.sourceArticleCount = sourceArticleCount;
    }
}
