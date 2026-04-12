package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 피드백에 따른 user_interest 가중치 업데이트 서비스
 *
 * REQUIRES_NEW 트랜잭션으로 실행되어, 가중치 실패가 피드백 저장 트랜잭션에 영향을 주지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterInterestWeightService {

    private static final BigDecimal HELPFUL_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal NOT_HELPFUL_WEIGHT = BigDecimal.ONE.negate();
    private static final Set<TagType> WEIGHT_TARGET_TYPES = Set.of(TagType.SECTOR, TagType.STOCK);

    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final UserInterestRepository userInterestRepository;

    /**
     * 클러스터 소속 기사의 SECTOR/STOCK 태그에 대한 가중치를 원자적으로 업데이트한다.
     * 별도 트랜잭션으로 실행되어 실패 시 호출부 트랜잭션에 영향을 주지 않는다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateWeights(UUID userId, Long clusterId, FeedbackType feedbackType) {
        if (feedbackType == null) {
            throw new IllegalArgumentException("feedbackType은 null일 수 없습니다");
        }
        BigDecimal delta = feedbackType == FeedbackType.HELPFUL ? HELPFUL_WEIGHT : NOT_HELPFUL_WEIGHT;

        List<Long> articleIds = clusterArticleRepository.findByNewsClusterId(clusterId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();

        if (articleIds.isEmpty()) {
            return;
        }

        record TagKey(String type, String code) {}
        Set<TagKey> tagKeys = articleTagRepository.findByNewsArticleIdIn(articleIds).stream()
                .filter(t -> WEIGHT_TARGET_TYPES.contains(t.getTagType()))
                .map(t -> new TagKey(t.getTagType().name(), t.getTagCode()))
                .collect(Collectors.toSet());

        for (TagKey key : tagKeys) {
            userInterestRepository.upsertWeight(userId, key.type(), key.code(), delta);
        }

        log.info("피드백 가중치 업데이트 — clusterId: {}, type: {}, 태그 수: {}",
                clusterId, feedbackType, tagKeys.size());
        log.debug("피드백 가중치 상세 — userId: {}, clusterId: {}", userId, clusterId);
    }
}
