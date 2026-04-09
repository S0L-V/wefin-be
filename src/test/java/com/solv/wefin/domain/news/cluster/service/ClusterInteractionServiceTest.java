package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
    @Mock private NewsClusterArticleRepository clusterArticleRepository;
    @Mock private NewsArticleTagRepository articleTagRepository;
    @Mock private UserInterestRepository userInterestRepository;

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
    @DisplayName("submitFeedback — 정상 저장 + 가중치 업데이트")
    void submitFeedback_success() {
        given(newsClusterRepository.findById(CLUSTER_ID)).willReturn(Optional.of(activeCluster()));
        given(feedbackRepository.existsByUserIdAndNewsClusterId(USER_ID, CLUSTER_ID)).willReturn(false);
        given(clusterArticleRepository.findByNewsClusterId(CLUSTER_ID))
                .willReturn(List.of(NewsClusterArticle.create(CLUSTER_ID, 100L, 0, false)));
        given(articleTagRepository.findByNewsArticleIdIn(List.of(100L)))
                .willReturn(List.of(stockTag(100L, "005930")));
        given(userInterestRepository.addWeightAtomically(eq(USER_ID), eq("STOCK"), eq("005930"), any()))
                .willReturn(1);

        service.submitFeedback(USER_ID, CLUSTER_ID, FeedbackType.HELPFUL);

        verify(feedbackRepository).save(any());
        verify(userInterestRepository).addWeightAtomically(USER_ID, "STOCK", "005930", BigDecimal.ONE);
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
        given(clusterArticleRepository.findByNewsClusterId(CLUSTER_ID))
                .willThrow(new RuntimeException("DB 오류"));

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

    private NewsArticleTag stockTag(Long articleId, String code) {
        return NewsArticleTag.builder()
                .newsArticleId(articleId)
                .tagType(NewsArticleTag.TagType.STOCK)
                .tagCode(code)
                .tagName("삼성전자")
                .build();
    }
}
