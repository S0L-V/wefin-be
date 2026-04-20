package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.news.config.NewsHotProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

    private ClusterInteractionService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long CLUSTER_ID = 1L;
    // windowHours=3, aggIntervalSeconds=300, initialDelaySeconds=30, maxSize=20, throttleSeconds=60
    private static final NewsHotProperties HOT_PROPS =
            new NewsHotProperties(3, 300, 30, 20, 60);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new ClusterInteractionService(
                newsClusterRepository, readRepository, feedbackRepository,
                interestWeightService, HOT_PROPS);
    }

    @Test
    @DisplayName("markRead — 첫 방문: UPSERT INSERT 성공 시 unique_viewer_count +1 호출")
    void markRead_firstVisit_incrementsUniqueViewer() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(readRepository.insertIfAbsent(eq(USER_ID), eq(CLUSTER_ID), any())).willReturn(1);
        given(newsClusterRepository.incrementUniqueViewerCount(CLUSTER_ID)).willReturn(1);

        service.markRead(USER_ID, CLUSTER_ID);

        verify(newsClusterRepository).incrementUniqueViewerCount(CLUSTER_ID);
        verify(readRepository, never()).touchReadAtIfStale(any(), any(), any(), any());
    }

    @Test
    @DisplayName("markRead — 재방문: INSERT skip → touchReadAtIfStale 호출 (throttle 조건으로 DB가 판단)")
    void markRead_revisit_callsTouch() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(readRepository.insertIfAbsent(eq(USER_ID), eq(CLUSTER_ID), any())).willReturn(0);
        given(readRepository.touchReadAtIfStale(eq(USER_ID), eq(CLUSTER_ID), any(), any()))
                .willReturn(1);

        service.markRead(USER_ID, CLUSTER_ID);

        verify(newsClusterRepository, never()).incrementUniqueViewerCount(anyLong());
        verify(readRepository).touchReadAtIfStale(eq(USER_ID), eq(CLUSTER_ID), any(), any());
    }

    @Test
    @DisplayName("markRead — INSERT 성공했지만 increment 결과 0이면 경고 로그 후 정상 반환")
    void markRead_firstVisit_incrementMiss_logsWarn() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(readRepository.insertIfAbsent(eq(USER_ID), eq(CLUSTER_ID), any())).willReturn(1);
        given(newsClusterRepository.incrementUniqueViewerCount(CLUSTER_ID)).willReturn(0);

        service.markRead(USER_ID, CLUSTER_ID);

        verify(newsClusterRepository).incrementUniqueViewerCount(CLUSTER_ID);
        verify(readRepository, never()).touchReadAtIfStale(any(), any(), any(), any());
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

        service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);

        verify(feedbackRepository).saveAndFlush(any());
        verify(interestWeightService).updateWeights(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);
    }

    @Test
    @DisplayName("submitFeedback — 중복 시 DUPLICATE_RESOURCE")
    void submitFeedback_duplicate() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(feedbackRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("submitFeedback — 가중치 실패해도 피드백은 저장됨")
    void submitFeedback_weightFailure_feedbackSaved() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        doThrow(new RuntimeException("가중치 오류"))
                .when(interestWeightService).updateWeights(any(), any(), any());

        service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);

        verify(feedbackRepository).saveAndFlush(any());
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
