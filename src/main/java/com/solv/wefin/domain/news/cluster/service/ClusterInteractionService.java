package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 클러스터 읽음 처리 및 피드백 서비스
 *
 * 읽음: 중복 시 무시 (idempotent)
 * 피드백: 1회만 가능, 중복 시 409
 * 가중치 업데이트는 ClusterInterestWeightService에서 별도 트랜잭션으로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterInteractionService {

    private final NewsClusterRepository newsClusterRepository;
    private final UserNewsClusterReadRepository readRepository;
    private final UserNewsClusterFeedbackRepository feedbackRepository;
    private final ClusterInterestWeightService interestWeightService;

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
     */
    @Transactional
    public void submitFeedback(UUID userId, Long clusterId, FeedbackType feedbackType) {
        validateActiveCluster(clusterId);

        try {
            feedbackRepository.saveAndFlush(UserNewsClusterFeedback.create(userId, clusterId, feedbackType));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        try {
            interestWeightService.updateWeights(userId, clusterId, feedbackType);
        } catch (Exception e) {
            log.warn("가중치 업데이트 실패 (피드백은 저장됨) — userId: {}, clusterId: {}",
                    userId, clusterId, e);
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
}
