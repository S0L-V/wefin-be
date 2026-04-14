package com.solv.wefin.domain.market.trend.dto;

import java.util.List;

/**
 * 금융 동향 인사이트 카드
 *
 * AI가 뉴스 클러스터 태그 집계를 바탕으로 생성한 카드 단위 인사이트.
 * {@code relatedClusterIds}는 프론트에서 상세 페이지(/news/:clusterId)로 이동할 때 사용된다
 */
public record InsightCard(
        String headline,
        String body,
        List<Long> relatedClusterIds
) {
}
