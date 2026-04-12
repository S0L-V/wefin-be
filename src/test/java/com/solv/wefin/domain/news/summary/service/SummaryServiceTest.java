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
import com.solv.wefin.domain.news.summary.dto.SummaryResult.SectionItem;
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

        SummaryResult result = createSummaryResult("테스트 제목", "테스트 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(openAiSummaryClient).generateSummary(any());
        verify(persistenceService).markGeneratedWithSections(eq(10L), eq("테스트 제목"), eq("테스트 요약"), any(), any());
        verify(persistenceService, never()).markFailed(any());
    }

    @Test
    @DisplayName("단독 클러스터 — AI 본문 요약 성공 시 AI 결과 사용")
    void generatePendingSummaries_singleArticle_aiSuccess() {
        // given
        NewsCluster cluster = createCluster(10L, 1, SummaryStatus.PENDING);
        NewsArticle article = createArticle(1L);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(NewsClusterArticle.create(10L, 1L, 1, false)));
        given(newsArticleRepository.findById(1L))
                .willReturn(Optional.of(article));

        SummaryResult aiResult = mock(SummaryResult.class);
        given(aiResult.getTitle()).willReturn("AI 생성 제목");
        given(aiResult.getLeadSummary()).willReturn("AI 생성 요약");
        given(openAiSummaryClient.generateSingleArticleSummary(any(), any()))
                .willReturn(aiResult);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(openAiSummaryClient).generateSingleArticleSummary(any(), any());
        verify(openAiSummaryClient, never()).generateSummary(any());
        verify(persistenceService).markGeneratedSingle(eq(10L), eq("AI 생성 제목"), eq("AI 생성 요약"), any());
    }

    @Test
    @DisplayName("단독 클러스터 — AI 실패 시 기사 제목/요약 fallback")
    void generatePendingSummaries_singleArticle_aiFail_fallback() {
        // given
        NewsCluster cluster = createCluster(10L, 1, SummaryStatus.PENDING);
        NewsArticle article = createArticle(1L);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(NewsClusterArticle.create(10L, 1L, 1, false)));
        given(newsArticleRepository.findById(1L))
                .willReturn(Optional.of(article));
        given(openAiSummaryClient.generateSingleArticleSummary(any(), any()))
                .willThrow(new RuntimeException("AI 호출 실패"));

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markGeneratedSingle(eq(10L), eq("테스트 기사 1"), any(), any());
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
        verify(persistenceService, never()).markGeneratedWithSections(any(), any(), any(), any(), any());
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
        verify(persistenceService, never()).markGeneratedWithSections(any(), any(), any(), any(), any());
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

        SummaryResult result = createSummaryResult("갱신된 제목", "갱신된 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markGeneratedWithSections(eq(10L), eq("갱신된 제목"), eq("갱신된 요약"), any(), any());
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

        SummaryResult result = createSummaryResult("정제된 제목", "정제된 요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(outlierDetectionService).removeOutliers(cluster);
        verify(persistenceService).markGeneratedWithSections(eq(10L), eq("정제된 제목"), eq("정제된 요약"), any(), any());
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
        verify(persistenceService, never()).markGeneratedWithSections(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("섹션이 비어있으면 FAILED 마킹")
    void generatePendingSummaries_emptySections_markFailed() {
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
        ReflectionTestUtils.setField(result, "title", "제목");
        ReflectionTestUtils.setField(result, "leadSummary", "요약");

        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markFailed(10L);
        verify(persistenceService, never()).markGeneratedWithSections(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("섹션에 출처가 없으면 FAILED 마킹")
    void generatePendingSummaries_sectionsWithoutSources_markFailed() {
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

        // 유효한 heading/body는 있지만 sourceArticleIndices가 없는 섹션
        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", "제목");
        ReflectionTestUtils.setField(result, "leadSummary", "요약");
        SectionItem section = new SectionItem();
        ReflectionTestUtils.setField(section, "heading", "소제목");
        ReflectionTestUtils.setField(section, "body", "본문");
        // sourceArticleIndices가 null → hasSources() == false
        ReflectionTestUtils.setField(result, "sections", List.of(section));

        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);

        // when
        summaryService.generatePendingSummaries();

        // then
        verify(persistenceService).markFailed(10L);
        verify(persistenceService, never()).markGeneratedWithSections(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기사 집합 불일치(StaleClusterException) 시 markFailed를 호출하지 않는다")
    void generatePendingSummaries_staleCluster_skipWithoutMarkFailed() {
        // given
        NewsCluster cluster = createCluster(10L, 3, SummaryStatus.STALE);

        given(newsClusterRepository.findByStatusAndSummaryStatusIn(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(outlierDetectionService.removeOutliers(cluster))
                .willReturn(0);
        given(clusterArticleRepository.findByNewsClusterId(10L))
                .willReturn(List.of(
                        NewsClusterArticle.create(10L, 1L, 1, false),
                        NewsClusterArticle.create(10L, 2L, 2, false),
                        NewsClusterArticle.create(10L, 3L, 3, false)));
        given(newsArticleRepository.findAllById(any()))
                .willReturn(List.of(createArticle(1L), createArticle(2L), createArticle(3L)));

        SummaryResult result = createSummaryResult("제목", "요약");
        given(openAiSummaryClient.generateSummary(any()))
                .willReturn(result);
        doThrow(new StaleClusterException("기사 집합 변경"))
                .when(persistenceService).markGeneratedWithSections(any(), any(), any(), any(), any());

        // when
        summaryService.generatePendingSummaries();

        // then — markFailed가 호출되지 않아야 한다
        verify(persistenceService, never()).markFailed(any());
    }

    /**
     * 유효한 섹션(heading + body + sourceArticleIndices)을 포함한 SummaryResult를 생성한다
     */
    private SummaryResult createSummaryResult(String title, String leadSummary) {
        SummaryResult result = new SummaryResult();
        ReflectionTestUtils.setField(result, "title", title);
        ReflectionTestUtils.setField(result, "leadSummary", leadSummary);

        SectionItem section = new SectionItem();
        ReflectionTestUtils.setField(section, "heading", "테스트 소제목");
        ReflectionTestUtils.setField(section, "body", "테스트 본문 내용");
        ReflectionTestUtils.setField(section, "sourceArticleIndices", List.of(1, 2));

        ReflectionTestUtils.setField(result, "sections", List.of(section));
        return result;
    }
}
