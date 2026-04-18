package com.solv.wefin.web.news.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.service.NewsRecommendationService.RecommendationResult;

import java.util.List;
import java.util.Map;

/**
 * 추천 뉴스 카드 API 응답 DTO
 *
 * 타입별 최신 카드 목록과 "더 불러오기" 가능 여부를 전달한다.
 * linkedCluster는 조회 시점에 INACTIVE/삭제된 경우 null로 반환한다
 */
public record RecommendedNewsResponse(
        List<CardResponse> cards,
        boolean hasMore,
        int refreshCount,
        int refreshLimit
) {

    /**
     * 서비스 결과를 API 응답으로 변환한다
     *
     * @param result       추천 결과 (카드 + 연결 클러스터 매핑 포함)
     * @param objectMapper reasons JSON 파싱용
     */
    public static RecommendedNewsResponse from(RecommendationResult result,
                                                ObjectMapper objectMapper) {
        List<CardResponse> cards = result.cards().stream()
                .map(card -> CardResponse.from(card, result.linkedClusterMap(), objectMapper))
                .toList();
        return new RecommendedNewsResponse(cards, result.hasMore(),
                result.refreshCount(), result.refreshLimit());
    }

    public record CardResponse(
            String cardType,
            String interestCode,
            String interestName,
            String title,
            String summary,
            String context,
            List<ReasonResponse> reasons,
            LinkedClusterResponse linkedCluster
    ) {
        static CardResponse from(RecommendedNewsCard card,
                                 Map<Long, NewsCluster> clusterMap,
                                 ObjectMapper objectMapper) {
            List<ReasonResponse> reasons = parseReasons(card.getReasons(), objectMapper);

            NewsCluster cluster = clusterMap.get(card.getLinkedClusterId());
            LinkedClusterResponse linkedCluster = cluster != null
                    ? new LinkedClusterResponse(cluster.getId(), cluster.getTitle())
                    : null;

            return new CardResponse(
                    card.getCardType().name(),
                    card.getInterestCode(),
                    card.getInterestName(),
                    card.getTitle(),
                    card.getSummary(),
                    card.getContext(),
                    reasons,
                    linkedCluster
            );
        }

        private static List<ReasonResponse> parseReasons(String reasonsJson,
                                                          ObjectMapper objectMapper) {
            try {
                List<Map<String, Object>> raw = objectMapper.readValue(
                        reasonsJson, new TypeReference<>() {});
                return raw.stream()
                        .map(m -> new ReasonResponse(
                                m.get("type") instanceof String s ? s : null,
                                m.get("count") instanceof Number n ? n.intValue() : null,
                                m.get("label") instanceof String s ? s : null))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    public record ReasonResponse(
            String type,
            Integer count,
            String label
    ) {
    }

    public record LinkedClusterResponse(
            Long clusterId,
            String title
    ) {
    }
}
