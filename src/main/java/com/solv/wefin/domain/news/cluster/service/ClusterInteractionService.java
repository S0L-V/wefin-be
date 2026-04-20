package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.news.config.NewsHotProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 클러스터 읽음 처리 및 피드백 서비스
 *
 * 읽음: UPSERT 기반 — 첫 방문 시 insert 후 uniqueViewerCount 증가, 재방문 시 read_at 갱신(짧은 간격은 throttle)
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
    private final NewsHotProperties newsHotProperties;
    private final Clock clock;

    /**
     * 클러스터 읽음을 기록한다.
     *
     * 동작:
     * - 해당 유저가 처음 보는 클러스터면 INSERT 하고 {@code unique_viewer_count} 를 원자적으로 +1
     * - 이미 본 클러스터면 read_at 을 현재 시각으로 갱신 (단, 최근 throttle 윈도우 내 재호출이면 skip)
     *
     * {@code recent_view_count} 는 배치가 집계하므로 여기서 건드리지 않는다.
     * 동시 INSERT 는 unique 제약 + {@code ON CONFLICT DO NOTHING} 으로 한쪽만 성공하도록 보장.
     *
     * 관측:
     * - result=throttled: 같은 userId+clusterId 가 throttle 윈도우(기본 60s) 내 반복 호출 →
     *   FE 가 세션 debounce 계약을 지키지 않거나 어뷰저 가능성. debug 로그로 집계하여 이상치 탐지.
     * - increment_missed: insertIfAbsent 는 성공했으나 status 가드에 걸려 count 증가 실패 → INACTIVE race
     */
    @Transactional
    public void markRead(UUID userId, Long clusterId) {
        validateActiveCluster(clusterId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime staleThreshold = now.minusSeconds(newsHotProperties.markReadThrottleSeconds());

        int inserted = readRepository.insertIfAbsent(userId, clusterId, now);

        if (inserted == 1) {
            int affected = newsClusterRepository.incrementUniqueViewerCount(clusterId, ClusterStatus.ACTIVE);
            if (affected == 0) {
                log.warn("[markRead] result=increment_missed reason=inactive_race_or_deleted clusterId={}",
                        clusterId);
            }
            return;
        }

        // 재방문 — 최근 throttle 윈도우 내라면 자연스럽게 touchAffected=0 이 되어 write 없이 종료.
        // 양쪽 모두 0이면 throttle 내 반복 호출이라는 의미 → FE 계약 위반 / 어뷰징 신호.
        // 로그에는 userId 를 남기지 않는다(PII). 어뷰저 단위 집계는 추후 Micrometer 도입 시
        // IP/Account-hash 기반 태그로 재구성.
        int touchAffected = readRepository.touchReadAtIfStale(userId, clusterId, now, staleThreshold);
        if (touchAffected == 0) {
            log.debug("[markRead] result=throttled clusterId={} thresholdSeconds={}",
                    clusterId, newsHotProperties.markReadThrottleSeconds());
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
