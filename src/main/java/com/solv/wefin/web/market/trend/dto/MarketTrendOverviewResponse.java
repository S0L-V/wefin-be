package com.solv.wefin.web.market.trend.dto;

import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.trend.dto.InsightCard;
import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.dto.SourceClusterInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 금융 동향 overview API 응답
 *
 * - {@code generated=false}이면 AI 동향은 아직 미생성.
 */
public record MarketTrendOverviewResponse(
        boolean generated,
        LocalDate trendDate,
        String title,
        String summary,
        List<InsightCardResponse> insightCards,
        List<String> relatedKeywords,
        List<SourceClusterResponse> sourceClusters,
        int sourceClusterCount,
        int sourceArticleCount,
        OffsetDateTime updatedAt,
        List<MarketSnapshotResponse> marketSnapshots
) {

    public static MarketTrendOverviewResponse from(MarketTrendOverview overview) {
        List<InsightCardResponse> cards = overview.insightCards().stream()
                .map(InsightCardResponse::from)
                .toList();
        List<MarketSnapshotResponse> snapshots = overview.marketSnapshots().stream()
                .map(MarketSnapshotResponse::from)
                .toList();
        List<SourceClusterResponse> sources = overview.sourceClusters().stream()
                .map(SourceClusterResponse::from)
                .toList();
        return new MarketTrendOverviewResponse(
                overview.generated(),
                overview.trendDate(),
                overview.title(),
                overview.summary(),
                cards,
                overview.relatedKeywords(),
                sources,
                sources.size(),
                overview.sourceArticleCount(),
                overview.updatedAt(),
                snapshots
        );
    }

    public record SourceClusterResponse(
            Long clusterId,
            String title,
            OffsetDateTime publishedAt
    ) {
        public static SourceClusterResponse from(SourceClusterInfo info) {
            return new SourceClusterResponse(info.clusterId(), info.title(), info.publishedAt());
        }
    }

    public record InsightCardResponse(
            String headline,
            String body,
            List<Long> relatedClusterIds
    ) {
        public static InsightCardResponse from(InsightCard card) {
            return new InsightCardResponse(card.headline(), card.body(), card.relatedClusterIds());
        }
    }

    public record MarketSnapshotResponse(
            String metricType,
            String label,
            BigDecimal value,
            BigDecimal changeRate,
            BigDecimal changeValue,
            String unit,
            String changeDirection
    ) {
        public static MarketSnapshotResponse from(MarketSnapshot s) {
            return new MarketSnapshotResponse(
                    s.getMetricType().name(),
                    s.getLabel(),
                    s.getValue(),
                    s.getChangeRate(),
                    s.getChangeValue(),
                    s.getUnit().name(),
                    s.getChangeDirection().name()
            );
        }
    }
}
