package com.solv.wefin.domain.trading.stock.news.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 뉴스팀 API (dev-api.wefin.ai.kr/api/news/clusters) 응답 원본.
 * 뉴스팀은 자체 ApiResponse 래핑을 함: { status, code, message, data: { items, hasNext, nextCursor } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WefinNewsApiResponse(
        Integer status,
        String code,
        String message,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            List<ClusterItem> items,
            boolean hasNext,
            String nextCursor
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClusterItem(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            String publishedAt,
            Integer sourceCount,
            List<Source> sources
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String publisherName,
            String url
    ) {
    }
}
