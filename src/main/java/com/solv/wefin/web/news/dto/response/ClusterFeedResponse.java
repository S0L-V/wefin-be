package com.solv.wefin.web.news.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedItem;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.domain.news.cluster.dto.SourceInfo;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 클러스터 피드 API 응답 DTO
 *
 * - Service 레이어의 ClusterFeedResult를 API 응답 형태로 변환
 * - 커서 기반 페이지네이션 정보를 문자열 형태로 가공하여 전달
 * - sort=view 응답에서는 {@code lastAggregatedAt} 으로 배치 마지막 성공 시각을 노출해
 *   FE 가 집계 지연 시 폴백(sort=publishedAt)을 판단할 수 있게 한다.
 *   sort 가 publishedAt/updatedAt 인 응답에서는 null 이며 {@code @JsonInclude(NON_NULL)} 로 응답에서 생략됨.
 */
public record ClusterFeedResponse(
        List<ClusterItemResponse> items,
        boolean hasNext,
        String nextCursor,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime lastAggregatedAt
) {

    /**
     * Service 결과 → API 응답 DTO 변환
     *
     * 1) 내부 ClusterFeedItem → ClusterItemResponse로 변환
     * 2) nextCursor를 문자열로 인코딩
     * 3) lastAggregatedAt 은 sort=view 응답에서만 값이 들어오고 그 외에는 null
     */
    public static ClusterFeedResponse from(ClusterFeedResult result) {

        // 각 클러스터 아이템을 응답 DTO로 변환
        List<ClusterItemResponse> items = result.items().stream()
                .map(ClusterItemResponse::from)
                .toList();

        // 다음 커서 생성
        String nextCursor = result.hasNext() && result.nextCursorPublishedAt() != null
                ? result.nextCursorPublishedAt().toInstant().toEpochMilli()
                + "_" + result.nextCursorId()
                : null;

        return new ClusterFeedResponse(items, result.hasNext(), nextCursor, result.lastAggregatedAt());
    }

    /**
     * 클러스터 단일 아이템 응답 DTO
     *
     * @param recentViewCount 최근 N시간(기본 3h) 내 이 클러스터를 본 고유 유저 수
     */
    public record ClusterItemResponse(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            int sourceCount,
            List<SourceResponse> sources,
            List<StockResponse> relatedStocks,
            List<String> marketTags,
            boolean isRead,
            long recentViewCount
    ) {
        public static ClusterItemResponse from(ClusterFeedItem item) {
            return new ClusterItemResponse(
                    item.clusterId(),
                    item.title(),
                    item.summary(),
                    item.thumbnailUrl(),
                    item.publishedAt(),
                    item.sourceCount(),
                    item.sources().stream()
                            .map(SourceResponse::from)
                            .toList(),
                    item.relatedStocks().stream().map(StockResponse::from).toList(),
                    item.marketTags(),
                    item.isRead(),
                    item.recentViewCount()
            );
        }
    }

    /**
     * 출처 정보 응답 DTO
     *
     * - publisherName: 언론사 이름
     * - url: 원본 기사 링크
     */
    public record SourceResponse(String publisherName, String url) {

        /**
         * Service SourceInfo → Response DTO 변환
         */
        public static SourceResponse from(SourceInfo source) {
            return new SourceResponse(
                    source.publisherName(),
                    source.url()
            );
        }
    }

    public record StockResponse(String code, String name) {
        public static StockResponse from(StockInfo stock) {
            return new StockResponse(stock.code(), stock.name());
        }
    }
}
