package com.solv.wefin.domain.market.trend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 금융 동향 인사이트 카드
 *
 * AI가 뉴스 클러스터 태그 집계를 바탕으로 생성한 카드 단위 인사이트
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InsightCard(
        String headline,
        String body,
        String advice,
        String adviceLabel,
        List<Long> relatedClusterIds
) {

    /** overview 등 조언이 없는 카드 생성 헬퍼 */
    public static InsightCard withoutAdvice(String headline, String body, List<Long> relatedClusterIds) {
        return new InsightCard(headline, body, null, null, relatedClusterIds);
    }
}
