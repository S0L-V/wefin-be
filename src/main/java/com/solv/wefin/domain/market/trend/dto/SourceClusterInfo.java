package com.solv.wefin.domain.market.trend.dto;

import java.time.OffsetDateTime;

/**
 * 금융 동향 생성에 사용된 클러스터 출처 정보
 */
public record SourceClusterInfo(
        Long clusterId,
        String title,
        OffsetDateTime publishedAt
) {
}
