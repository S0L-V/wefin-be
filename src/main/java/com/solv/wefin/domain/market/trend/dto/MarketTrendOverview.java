package com.solv.wefin.domain.market.trend.dto;

import com.solv.wefin.domain.market.entity.MarketSnapshot;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 금융 동향 overview 조회 결과
 *
 */
public record MarketTrendOverview(
        boolean generated,
        LocalDate trendDate,
        String title,
        String summary,
        List<InsightCard> insightCards,
        List<String> relatedKeywords,
        List<SourceClusterInfo> sourceClusters,
        int sourceArticleCount,
        OffsetDateTime updatedAt,
        List<MarketSnapshot> marketSnapshots
) {
    public static MarketTrendOverview empty(List<MarketSnapshot> snapshots) {
        return new MarketTrendOverview(false, null, null, null, List.of(), List.of(),
                List.of(), 0, null, snapshots);
    }
}
