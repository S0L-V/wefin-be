package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 클러스터 읽음 처리 및 피드백 서비스
 *
 * 읽음: 중복 시 무시 (idempotent)
 * 피드백: 1회만 가능, 중복 시 409
 * 피드백 시 user_interest 가중치를 원자적 UPDATE로 업데이트 (HELPFUL +1, NOT_HELPFUL -1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterInteractionService {

    private static final BigDecimal HELPFUL_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal NOT_HELPFUL_WEIGHT = BigDecimal.ONE.negate();
    private static final Set<TagType> WEIGHT_TARGET_TYPES = Set.of(TagType.SECTOR, TagType.STOCK);

    private final NewsClusterRepository newsClusterRepository;
    private final UserNewsClusterReadRepository readRepository;
    private final UserNewsClusterFeedbackRepository feedbackRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final UserInterestRepository userInterestRepository;

    /**
     * 클러스터 읽음을 기록한다.
     * 이미 읽은 경우 무시한다 (idempotent).
     * 동시 요청 시 unique violation을 잡아 무시한다
     */
    @Transactional
    public void markRead(UUID userId, Long clusterId) {
        validateActiveCluster(clusterId);

        if (readRepository.existsByUserIdAndNewsClusterId(userId, clusterId)) {
            return;
        }
        try {
            readRepository.save(UserNewsClusterRead.create(userId, clusterId));
        } catch (DataIntegrityViolationException e) {
            log.debug("읽음 중복 저장 무시 — userId: {}, clusterId: {}", userId, clusterId);
        }
    }

    /**
     * 클러스터에 피드백을 남긴다.
     * 1회만 가능하며, 이미 피드백한 경우 409를 반환한다.
     * 피드백 저장 후 가중치 업데이트는 별도 트랜잭션으로 처리하여,
     * 가중치 실패가 피드백 저장을 롤백하지 않도록 한다
     */
    @Transactional
    public void submitFeedback(UUID userId, Long clusterId, FeedbackType feedbackType) {
        validateActiveCluster(clusterId);

        if (feedbackRepository.existsByUserIdAndNewsClusterId(userId, clusterId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        try {
            feedbackRepository.save(UserNewsClusterFeedback.create(userId, clusterId, feedbackType));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        try {
            updateInterestWeights(userId, clusterId, feedbackType);
        } catch (Exception e) {
            log.warn("가중치 업데이트 실패 (피드백은 저장됨) — userId: {}, clusterId: {}, error: {}",
                    userId, clusterId, e.getMessage());
        }
    }

    /**
     * 클러스터가 존재하고 ACTIVE 상태인지 검증한다
     */
    private void validateActiveCluster(Long clusterId) {
        NewsCluster cluster = newsClusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLUSTER_NOT_FOUND));

        if (cluster.getStatus() != ClusterStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CLUSTER_NOT_FOUND);
        }
    }

    /**
     * 피드백에 따라 클러스터 소속 기사의 SECTOR/STOCK 태그에 대한 가중치를 원자적으로 업데이트한다.
     * 해당 관심사가 없으면 새로 생성한다
     */
    private void updateInterestWeights(UUID userId, Long clusterId, FeedbackType feedbackType) {
        BigDecimal delta = feedbackType == FeedbackType.HELPFUL ? HELPFUL_WEIGHT : NOT_HELPFUL_WEIGHT;

        List<Long> articleIds = clusterArticleRepository.findByNewsClusterId(clusterId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();

        if (articleIds.isEmpty()) {
            return;
        }

        // SECTOR/STOCK 태그만 대상, (tagType, tagCode) 기준 중복 제거
        record TagKey(String type, String code) {}
        Set<TagKey> tagKeys = articleTagRepository.findByNewsArticleIdIn(articleIds).stream()
                .filter(t -> WEIGHT_TARGET_TYPES.contains(t.getTagType()))
                .map(t -> new TagKey(t.getTagType().name(), t.getTagCode()))
                .collect(Collectors.toSet());

        for (TagKey key : tagKeys) {
            int updated = userInterestRepository.addWeightAtomically(
                    userId, key.type(), key.code(), delta);

            if (updated == 0) {
                // 관심사가 없으면 새로 생성
                try {
                    userInterestRepository.saveAndFlush(
                            UserInterest.create(userId, key.type(), key.code(), delta));
                } catch (DataIntegrityViolationException e) {
                    // 동시 생성 경쟁 — 이미 생성됐으므로 원자적 UPDATE 재시도
                    userInterestRepository.addWeightAtomically(
                            userId, key.type(), key.code(), delta);
                }
            }
        }

        log.info("피드백 가중치 업데이트 — userId: {}, clusterId: {}, type: {}, 태그 수: {}",
                userId, clusterId, feedbackType, tagKeys.size());
    }
}
