package com.solv.wefin.domain.market.trend.repository;

import com.solv.wefin.domain.market.trend.entity.UserMarketTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UserMarketTrendRepository extends JpaRepository<UserMarketTrend, Long> {

    /**
     * 오늘자 캐시 row를 조회한다 (cache lookup)
     */
    Optional<UserMarketTrend> findByUserIdAndTrendDate(UUID userId, LocalDate trendDate);

    /**
     * 사용자별 맞춤 동향 캐시를 native upsert한다.
     *
     * 동일 (user_id, trend_date)에 충돌하면 콘텐츠를 최신화한다.
     * personalized=false 폴백 결과는 호출자가 저장하지 않는다 (TTL이 같은 날 안에 다시 시도 가능)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_market_trend (
                user_id, trend_date, mode, summary,
                insight_cards, related_keywords, source_cluster_ids,
                source_article_count, created_at, updated_at
            )
            VALUES (
                :userId, :trendDate, :mode, :summary,
                CAST(:insightCardsJson AS jsonb),
                CAST(:relatedKeywordsJson AS jsonb),
                CAST(:sourceClusterIdsJson AS jsonb),
                :sourceArticleCount, now(), now()
            )
            ON CONFLICT (user_id, trend_date) DO UPDATE SET
                mode                 = EXCLUDED.mode,
                summary              = EXCLUDED.summary,
                insight_cards        = EXCLUDED.insight_cards,
                related_keywords     = EXCLUDED.related_keywords,
                source_cluster_ids   = EXCLUDED.source_cluster_ids,
                source_article_count = EXCLUDED.source_article_count,
                updated_at           = now()
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId,
                @Param("trendDate") LocalDate trendDate,
                @Param("mode") String mode,
                @Param("summary") String summary,
                @Param("insightCardsJson") String insightCardsJson,
                @Param("relatedKeywordsJson") String relatedKeywordsJson,
                @Param("sourceClusterIdsJson") String sourceClusterIdsJson,
                @Param("sourceArticleCount") int sourceArticleCount);

    /**
     * 사용자의 오늘자 캐시를 삭제한다 (관심사 변경 시 무효화 트리거)
     */
    @Modifying(clearAutomatically = true)
    void deleteByUserIdAndTrendDate(UUID userId, LocalDate trendDate);
}
