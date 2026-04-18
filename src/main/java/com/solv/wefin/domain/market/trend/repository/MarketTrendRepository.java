package com.solv.wefin.domain.market.trend.repository;

import com.solv.wefin.domain.market.trend.entity.MarketTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface MarketTrendRepository extends JpaRepository<MarketTrend, Long> {

    /**
     * 지정 날짜의 동향을 조회한다 (session + 콘텐츠 존재 필터).
     */
    Optional<MarketTrend> findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(
            java.time.LocalDate trendDate, String session);

    /**
     * (trend_date, session) 기준으로 기존 row를 조회한다 (테스트 검증용)
     */
    Optional<MarketTrend> findByTrendDateAndSession(LocalDate trendDate, String session);

    /**
     * (trend_date, session) 충돌 시 콘텐츠를 갱신하는 native upsert.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO market_trend (
                trend_date, session, title, summary,
                insight_cards, related_keywords,
                source_cluster_ids, source_article_count,
                created_at, updated_at
            )
            VALUES (
                :trendDate, :session, :title, :summary,
                CAST(:insightCardsJson AS jsonb), CAST(:relatedKeywordsJson AS jsonb),
                CAST(:sourceClusterIdsJson AS jsonb), :sourceArticleCount,
                now(), now()
            )
            ON CONFLICT (trend_date, session) DO UPDATE SET
                title                = EXCLUDED.title,
                summary              = EXCLUDED.summary,
                insight_cards        = EXCLUDED.insight_cards,
                related_keywords     = EXCLUDED.related_keywords,
                source_cluster_ids   = EXCLUDED.source_cluster_ids,
                source_article_count = EXCLUDED.source_article_count,
                updated_at           = now()
            """, nativeQuery = true)
    void upsertDaily(@Param("trendDate") LocalDate trendDate,
                     @Param("session") String session,
                     @Param("title") String title,
                     @Param("summary") String summary,
                     @Param("insightCardsJson") String insightCardsJson,
                     @Param("relatedKeywordsJson") String relatedKeywordsJson,
                     @Param("sourceClusterIdsJson") String sourceClusterIdsJson,
                     @Param("sourceArticleCount") Integer sourceArticleCount);
}
