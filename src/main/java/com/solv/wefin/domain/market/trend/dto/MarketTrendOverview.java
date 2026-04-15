package com.solv.wefin.domain.market.trend.dto;

import com.solv.wefin.domain.market.entity.MarketSnapshot;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 금융 동향 overview/personalized 조회 결과
 *
 * {@code mode}는 personalized 엔드포인트에서만 의미를 가진다 ({@link PersonalizationMode}).
 * overview 엔드포인트 응답에서는 항상 {@code null}이다.
 *
 * {@code null} — /overview 엔드포인트 응답. personalized 분류 개념 미적용
 * {@code MATCHED} — /personalized + 관심사 매칭 분석
 * {@code ACTION_BRIEFING} — /personalized + 매칭 0건이라 일반 시장 액션 분석
 * {@code OVERVIEW_FALLBACK} — /personalized + 관심사 0개·클러스터 0건·AI 실패 등으로 overview 콘텐츠 재사용
 *
 *
 * {@code personalized()} 는 호환용 boolean accessor — {@code mode == MATCHED}와 동치
 */
public record MarketTrendOverview(
        boolean generated,
        PersonalizationMode mode,
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
    /** mode == MATCHED 동치. 응답 호환용 derived field */
    public boolean personalized() {
        return mode == PersonalizationMode.MATCHED;
    }

    public static MarketTrendOverview empty(List<MarketSnapshot> snapshots) {
        // overview 엔드포인트 빈 응답은 mode=null (personalized 분류 미적용)
        return new MarketTrendOverview(false, null, null,
                null, null, List.of(), List.of(), List.of(), 0, null, snapshots);
    }

    /** 새로운 mode로 복제한다. overview를 fallback으로 재사용할 때 사용 */
    public MarketTrendOverview withMode(PersonalizationMode newMode) {
        return new MarketTrendOverview(generated, newMode, trendDate, title, summary,
                insightCards, relatedKeywords, sourceClusters, sourceArticleCount, updatedAt,
                marketSnapshots);
    }
}
