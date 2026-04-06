package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.summary.client.OpenAiSummaryClient;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @InjectMocks
    private SummaryService summaryService;

    @Mock
    private NewsClusterRepository newsClusterRepository;

    @Mock
    private NewsClusterArticleRepository clusterArticleRepository;

    @Mock
    private NewsArticleRepository newsArticleRepository;

    @Mock
    private OpenAiSummaryClient openAiSummaryClient;

    @Mock
    private OutlierDetectionService outlierDetectionService;

    @Mock
    private SummaryPersistenceService persistenceService;

    private NewsCluster createCluster(Long id, int articleCount, SummaryStatus status) {
        float[] vector = {1.0f, 0.0f, 0.0f};
        NewsCluster cluster = NewsCluster.createSingle(vector, 1L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "id", id);
        ReflectionTestUtils.setField(cluster, "articleCount", articleCount);
        ReflectionTestUtils.setField(cluster, "summaryStatus", status);
        return cluster;
    }

    private NewsArticle createArticle(Long id) {
        NewsArticle article = NewsArticle.builder()
                .rawNewsArticleId(1L)
                .publisherName("테스트")
                .title("테스트 기사 " + id)
                .content("테스트 본문 " + id)
                .originalUrl("https://example.com/" + id)
                .build();
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    @Test
    @DisplayName("다건 클러스터 — AI 요약 생성 성공")
    void generatePendingSummaries_multiArticle_success() {
        // given
        NewsCluster cluster = createCluster(10L, 3, SummaryStatus.PENDING);
        NewsArticle article1 = createArticle(1L);
        NewsArticle article2 = createArticle(2L);
        NewsArticle article3 = createArticle(3L);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(0);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false),
                        NewsClusterArticle.create(10L, 3L, 3, false)));
        given(newsArticleRepository.findAllById(List.of(1L, 2L, 3L)))
                .willReturn(List.of(article1, article2, article3));

        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", "테스트 제목");
        ReflectionTestUtils.setField(result, "summary", "테스트 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(openAiSummaryClient).generateSummary(any());
        verify(persistenceService).markGenerated(10L, "테스트 제목", "테스트 요약");
        verify(persistenceService, never()).markFailed(any());
    }

    @Test
    @DisplayName("단독 클러스터 — AI 호출 없이 기사 제목/요약 사용")
    void generatePendingSummaries_singleArticle_noApiCall() {
        // given
        NewsCluster cluster = createCluster(10L, 1, SummaryStatus.PENDING);
        NewsArticle article = createArticle(1L);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(NewsClusterArticle.create(10L, 1L, 1, false)));
        given(newsArticleRepository.findAllById(List.of(1L)))
                .willReturn(List.of(article));
        given(newsArticleRepository.findById(1L))
                .willReturn(Optional.of(article));

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(openAiSummaryClient, never()).generateSummary(any());
        verify(persistenceService).markGenerated(eq(10L), eq("테스트 기사 1"), any());
    }

    @Test
    @DisplayName("AI 요약 실패 시 FAILED 마킹")
    void generatePendingSummaries_apiFailure_markFailed() {
        // given
        NewsCluster cluster = createCluster(10L, 2, SummaryStatus.PENDING);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(0);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false)));
        given(newsArticleRepository.findAllById(any()))
                .willReturn(List.of(createArticle(1L), createArticle(2L)));
        given(openAiSummaryClient.generateSummary(any()))
                .willThrow(new RuntimeException("API 오류"));

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markFailed(10L);
        verify(persistenceService, never()).markGenerated(any(), any(), any());
    }

    @Test
    @DisplayName("대상 클러스터가 없으면 아무것도 실행하지 않음")
    void generatePendingSummaries_noTargets() {
        // given
        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of());

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(openAiSummaryClient, never()).generateSummary(any());
        verify(persistenceService, never()).markGenerated(any(), any(), any());
    }

    @Test
    @DisplayName("STALE 클러스터도 요약 대상에 포함")
    void generatePendingSummaries_staleCluster() {
        // given
        NewsCluster cluster = createCluster(10L, 2, SummaryStatus.STALE);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(0);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false)));
        given(newsArticleRepository.findAllById(any()))
                .willReturn(List.of(createArticle(1L), createArticle(2L)));

        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", "갱신된 제목");
        ReflectionTestUtils.setField(result, "summary", "갱신된 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markGenerated(10L, "갱신된 제목", "갱신된 요약");
    }

    @Test
    @DisplayName("이상치 제거 후 요약 생성")
    void generatePendingSummaries_withOutlierRemoval() {
        // given
        NewsCluster cluster = createCluster(10L, 5, SummaryStatus.PENDING);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(2); // 2건 제거
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false),
                        NewsClusterArticle.create(10L, 3L, 3, false)));
        given(newsArticleRepository.findAllById(any()))
                .willReturn(List.of(createArticle(1L), createArticle(2L), createArticle(3L)));

        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", "정제된 제목");
        ReflectionTestUtils.setField(result, "summary", "정제된 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(outlierDetectionService).removeOutliers(cluster);
        verify(persistenceService).markGenerated(10L, "정제된 제목", "정제된 요약");
    }

    @Test
    @DisplayName("이상치 제거 후 기사 0건이면 FAILED 마킹")
    void generatePendingSummaries_emptyAfterOutlierRemoval_markFailed() {
        // given
        NewsCluster cluster = createCluster(10L, 3, SummaryStatus.PENDING);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(3);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of());
        given(newsArticleRepository.findAllById(List.of()))
                .willReturn(List.of());

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markFailed(10L);
        verify(openAiSummaryClient, never()).generateSummary(any());
    }

    @Test
    @DisplayName("AI 응답에 title만 있고 summary가 없으면 실패 처리")
    void generatePendingSummaries_partialResult_markFailed() {
        // given
        NewsCluster cluster = createCluster(10L, 2, SummaryStatus.PENDING);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(0);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false)));
        given(newsArticleRepository.findAllById(any()))
                .willReturn(List.of(createArticle(1L), createArticle(2L)));

        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", "제목만 있음");

        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markFailed(10L);
        verify(persistenceService, never()).markGenerated(any(), any(), any());
    }
}
