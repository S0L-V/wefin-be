package com.solv.wefin.domain.news.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 추천 카드의 reasons(추천 근거 라벨)을 실제 활동 데이터 기반으로 조립한다
 *
 * 데이터 소스:
 * - user_interest (manualRegistered=true) → REGISTERED_INTEREST
 * - user_news_cluster_read (최근 7일, 관심사 태그 매칭) → RECENT_READ
 * - user_news_cluster_feedback (최근 7일, HELPFUL, 관심사 태그 매칭) → HELPFUL_FEEDBACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationReasonBuilder {

    private static final int LOOKBACK_DAYS = 7;

    @PersistenceContext
    private EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * 주어진 관심사에 대한 추천 근거 라벨 목록을 JSON 문자열로 반환한다
     *
     * @param userId       사용자 식별자
     * @param cardType     카드 타입 (STOCK/SECTOR)
     * @param interestCode 관심사 코드 (종목 코드 또는 섹터 코드)
     * @param interestName 관심사 표시명
     * @return reasons JSON 배열 문자열
     */
    @Transactional(readOnly = true)
    public String buildReasonsJson(UUID userId, CardType cardType,
                                   String interestCode, String interestName) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        String tagType = cardType.name();

        if (isManuallyRegistered(userId, tagType, interestCode)) {
            String label = cardType == CardType.STOCK
                    ? interestName + "을(를) 관심 종목으로 등록하셨어요"
                    : interestName + " 분야를 관심 분야로 등록하셨어요";
            reasons.add(Map.of("type", "REGISTERED_INTEREST", "label", label));
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(LOOKBACK_DAYS);

        long readCount = countRecentReads(userId, tagType, interestCode, cutoff);
        if (readCount > 0) {
            reasons.add(Map.of(
                    "type", "RECENT_READ",
                    "count", readCount,
                    "label", "관련 뉴스를 " + readCount + "건 읽으셨어요"));
        }

        long helpfulCount = countRecentHelpfulFeedback(userId, tagType, interestCode, cutoff);
        if (helpfulCount > 0) {
            reasons.add(Map.of(
                    "type", "HELPFUL_FEEDBACK",
                    "count", helpfulCount,
                    "label", "관련 뉴스에 '도움돼요'를 " + helpfulCount + "회 누르셨어요"));
        }

        if (reasons.isEmpty()) {
            String label = cardType == CardType.STOCK
                    ? interestName + " 관련 뉴스를 모아봤어요"
                    : interestName + " 분야 뉴스를 모아봤어요";
            reasons.add(Map.of("type", "REGISTERED_INTEREST", "label", label));
        }

        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException e) {
            log.error("reasons JSON 직렬화 실패: userId={}, interestCode={}", userId, interestCode, e);
            return "[]";
        }
    }

    private boolean isManuallyRegistered(UUID userId, String interestType, String interestCode) {
        Long count = (Long) entityManager.createQuery(
                        "SELECT COUNT(ui) FROM UserInterest ui " +
                                "WHERE ui.userId = :userId " +
                                "AND ui.interestType = :type " +
                                "AND ui.interestValue = :code " +
                                "AND ui.manualRegistered = true")
                .setParameter("userId", userId)
                .setParameter("type", interestType)
                .setParameter("code", interestCode)
                .getSingleResult();
        return count > 0;
    }

    /**
     * 최근 N일간 해당 관심사 태그와 매칭되는 클러스터를 읽은 횟수를 반환한다
     *
     * user_news_cluster_read → news_cluster_article → news_article_tag 조인으로 집계
     */
    private long countRecentReads(UUID userId, String tagType,
                                  String interestCode, OffsetDateTime cutoff) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT r.news_cluster_id) " +
                                "FROM user_news_cluster_read r " +
                                "JOIN news_cluster_article nca ON nca.news_cluster_id = r.news_cluster_id " +
                                "JOIN news_article_tag t ON t.news_article_id = nca.news_article_id " +
                                "WHERE r.user_id = :userId " +
                                "AND r.read_at >= :cutoff " +
                                "AND t.tag_type = :tagType " +
                                "AND t.tag_code = :tagCode")
                .setParameter("userId", userId)
                .setParameter("cutoff", cutoff)
                .setParameter("tagType", tagType)
                .setParameter("tagCode", interestCode)
                .getSingleResult()).longValue();
    }

    /**
     * 최근 N일간 해당 관심사 태그와 매칭되는 클러스터에 HELPFUL 피드백을 준 횟수를 반환한다
     */
    private long countRecentHelpfulFeedback(UUID userId, String tagType,
                                            String interestCode, OffsetDateTime cutoff) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT f.news_cluster_id) " +
                                "FROM user_news_cluster_feedback f " +
                                "JOIN news_cluster_article nca ON nca.news_cluster_id = f.news_cluster_id " +
                                "JOIN news_article_tag t ON t.news_article_id = nca.news_article_id " +
                                "WHERE f.user_id = :userId " +
                                "AND f.submitted_at >= :cutoff " +
                                "AND f.feedback_type = 'HELPFUL' " +
                                "AND t.tag_type = :tagType " +
                                "AND t.tag_code = :tagCode")
                .setParameter("userId", userId)
                .setParameter("cutoff", cutoff)
                .setParameter("tagType", tagType)
                .setParameter("tagCode", interestCode)
                .getSingleResult()).longValue();
    }
}
