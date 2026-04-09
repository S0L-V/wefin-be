package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusterInteractionServiceTest {

    @Mock private NewsClusterRepository newsClusterRepository;
    @Mock private UserNewsClusterReadRepository readRepository;
    @Mock private UserNewsClusterFeedbackRepository feedbackRepository;
    @Mock private ClusterInterestWeightService interestWeightService;

    @InjectMocks
    private ClusterInteractionService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long CLUSTER_ID = 1L;

    @Test
    @DisplayName("markRead — 정상 저장")
    void markRead_success() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(readRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(false);

        service.markRead(USER_ID, CLUSTER_ID);

        verify(readRepository).save(any());
    }

    @Test
    @DisplayName("markRead — 중복 호출 시 idempotent")
    void markRead_duplicate_ignored() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(readRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(true);

        service.markRead(USER_ID, CLUSTER_ID);

        verify(readRepository, never()).save(any());
    }

    @Test
    @DisplayName("markRead — 존재하지 않는 클러스터 시 CLUSTER_NOT_FOUND")
    void markRead_clusterNotFound() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(USER_ID, CLUSTER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CLUSTER_NOT_FOUND);
    }

    @Test
    @DisplayName("markRead — INACTIVE 클러스터 시 CLUSTER_NOT_FOUND")
    void markRead_inactiveCluster() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(inactiveCluster()));

        assertThatThrownBy(() -> service.markRead(USER_ID, CLUSTER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CLUSTER_NOT_FOUND);
    }

    @Test
    @DisplayName("submitFeedback — 정상 저장 + 가중치 업데이트 호출")
    void submitFeedback_success() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(feedbackRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(false);

        service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);

        verify(feedbackRepository).save(any());
        verify(interestWeightService).updateWeights(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);
    }

    @Test
    @DisplayName("submitFeedback — 중복 시 DUPLICATE_RESOURCE")
    void submitFeedback_duplicate() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(feedbackRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(true);

        assertThatThrownBy(() -> service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("submitFeedback — 가중치 실패해도 피드백은 저장됨")
    void submitFeedback_weightFailure_feedbackSaved() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(feedbackRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(false);
        doThrow(new RuntimeException("가중치 오류"))
                .when(interestWeightService).updateWeights(any(), any(), any());

        service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);

        verify(feedbackRepository).save(any());
    }

    private NewsCluster activeCluster() {
        NewsCluster cluster = NewsCluster.createSingle(
                new float[]{0.1f}, 100L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "id", CLUSTER_ID);
        ReflectionTestUtils.setField(cluster, "status", ClusterStatus.ACTIVE);
        return cluster;
    }

    private NewsCluster inactiveCluster() {
        NewsCluster cluster = activeCluster();
        cluster.deactivate();
        return cluster;
    }
}
