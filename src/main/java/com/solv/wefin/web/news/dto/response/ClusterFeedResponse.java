package com.solv.wefin.web.news.dto.response;

import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedItem;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.SourceInfo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 클러스터 피드 API 응답 DTO
 *
 * - Service 레이어의 ClusterFeedResult를 API 응답 형태로 변환
 * - 커서 기반 페이지네이션 정보를 문자열 형태로 가공하여 전달
 * - publishedAt + id를 조합하여 커서 생성 (정렬 기준과 동일)
 */
public record ClusterFeedResponse(
        List<ClusterItemResponse> items,
        boolean hasNext,
        String nextCursor
) {

    /**
     * Service 결과 → API 응답 DTO 변환
     *
     * 1) 내부 ClusterFeedItem → ClusterItemResponse로 변환
     * 2) nextCursor를 문자열로 인코딩
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

        return new ClusterFeedResponse(items, result.hasNext(), nextCursor);
    }

    /**
     * 클러스터 단일 아이템 응답 DTO
     */
    public record ClusterItemResponse(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            int sourceCount, // 해당 클러스터에 포함된 전체 기사 수
            List<SourceResponse> sources, // 대표 출처 리스트 (최대 3개)
            List<String> relatedStocks, // 관련 종목 태그 목록
            boolean isRead // 사용자의 읽음 여부
    ) {

        /**
         * Service DTO → Response DTO 변환
         */
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
                    item.relatedStocks(),
                    item.isRead()
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
}