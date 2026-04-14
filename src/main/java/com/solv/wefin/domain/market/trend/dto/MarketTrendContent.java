package com.solv.wefin.domain.market.trend.dto;

import java.util.List;

/**
 * AI가 생성한 금융 동향 콘텐츠 (배치 내부 전달 + 저장 직전 형태)
 */
public record MarketTrendContent(
        String title,
        String summary,
        List<InsightCard> insightCards,
        List<String> relatedKeywords
) {
    public boolean isEmpty() {
        return (title == null || title.isBlank())
                || (summary == null || summary.isBlank());
    }
}
