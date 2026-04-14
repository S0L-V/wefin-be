package com.solv.wefin.domain.market.trend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.entity.MarketTrend;
import com.solv.wefin.domain.market.trend.repository.MarketTrendRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MarketTrendQueryServiceTest {

    @Mock private MarketTrendRepository marketTrendRepository;
    @Mock private MarketSnapshotRepository marketSnapshotRepository;
    @Mock private NewsClusterRepository newsClusterRepository;

    private MarketTrendQueryService queryService;

    private MarketSnapshot snapshot;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        queryService = new MarketTrendQueryService(
                marketTrendRepository, marketSnapshotRepository, newsClusterRepository, objectMapper);

        snapshot = MarketSnapshot.builder()
                .metricType(MarketSnapshot.MetricType.KOSPI)
                .label("코스피")
                .value(new BigDecimal("2700.00"))
                .changeRate(new BigDecimal("0.5"))
                .changeValue(new BigDecimal("13.50"))
                .unit(MarketSnapshot.Unit.POINT)
                .changeDirection(MarketSnapshot.ChangeDirection.UP)
                .build();
    }

    @Test
    @DisplayName("MarketTrend 없으면 generated=false로 snapshot만 반환")
    void getOverview_noTrend_returnsEmptyGenerated() {
        given(marketSnapshotRepository.findAll()).willReturn(List.of(snapshot));
        given(marketTrendRepository.findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(java.time.LocalDate.now(), MarketTrend.SESSION_DAILY)).willReturn(Optional.empty());

        MarketTrendOverview result = queryService.getOverview();

        assertThat(result.generated()).isFalse();
        assertThat(result.title()).isNull();
        assertThat(result.summary()).isNull();
        assertThat(result.insightCards()).isEmpty();
        assertThat(result.relatedKeywords()).isEmpty();
        assertThat(result.marketSnapshots()).hasSize(1);
    }

    @Test
    @DisplayName("MarketTrend 존재 — JSONB 직렬화 필드 파싱 + 클러스터 출처 보강")
    void getOverview_withTrend_parsesJsonAndResolvesSources() {
        given(marketSnapshotRepository.findAll()).willReturn(List.of(snapshot));

        String insightCardsJson = """
                [{"headline":"반도체 강세","body":"HBM 수요 증가","relatedClusterIds":[1,2]}]
                """;
        String keywordsJson = """
                ["반도체","HBM","엔비디아"]
                """;
        String sourceIdsJson = "[10, 20]";

        MarketTrend trend = MarketTrend.createDaily(
                LocalDate.now(), "오늘 시장 요약", "상세 내용",
                insightCardsJson, keywordsJson,
                sourceIdsJson, 56);
        ReflectionTestUtils.setField(trend, "updatedAt", OffsetDateTime.now());
        given(marketTrendRepository.findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(java.time.LocalDate.now(), MarketTrend.SESSION_DAILY)).willReturn(Optional.of(trend));

        // 클러스터 정보 보강 — ACTIVE + GENERATED/STALE만 노출
        NewsCluster c10 = createCluster(10L, "삼성전자 실적", OffsetDateTime.now().minusHours(1));
        NewsCluster c20 = createCluster(20L, "한은 금리 동결", OffsetDateTime.now().minusHours(2));
        given(newsClusterRepository.findByIdInAndStatusAndSummaryStatusIn(
                List.of(10L, 20L),
                ClusterStatus.ACTIVE,
                List.of(SummaryStatus.GENERATED, SummaryStatus.STALE)
        )).willReturn(List.of(c10, c20));

        MarketTrendOverview result = queryService.getOverview();

        assertThat(result.generated()).isTrue();
        assertThat(result.title()).isEqualTo("오늘 시장 요약");
        assertThat(result.insightCards()).hasSize(1);
        assertThat(result.relatedKeywords()).containsExactly("반도체", "HBM", "엔비디아");
        assertThat(result.sourceArticleCount()).isEqualTo(56);
        // 저장된 ID 입력 순서 유지
        assertThat(result.sourceClusters()).extracting(s -> s.clusterId()).containsExactly(10L, 20L);
        assertThat(result.sourceClusters()).extracting(s -> s.title())
                .containsExactly("삼성전자 실적", "한은 금리 동결");
    }

    @Test
    @DisplayName("저장된 클러스터 일부가 INACTIVE/요약 미생성이면 출처에서 제외")
    void getOverview_someClustersHidden_skipsMissing() {
        given(marketSnapshotRepository.findAll()).willReturn(List.of(snapshot));

        MarketTrend trend = MarketTrend.createDaily(
                LocalDate.now(), "제목", "요약", "[]", "[]",
                "[10, 20, 30]", 30);
        ReflectionTestUtils.setField(trend, "updatedAt", OffsetDateTime.now());
        given(marketTrendRepository.findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(java.time.LocalDate.now(), MarketTrend.SESSION_DAILY)).willReturn(Optional.of(trend));

        // 20번만 ACTIVE+GENERATED 상태로 반환됨 (10, 30은 노출 대상 아님)
        NewsCluster c20 = createCluster(20L, "유일 생존", OffsetDateTime.now());
        given(newsClusterRepository.findByIdInAndStatusAndSummaryStatusIn(
                List.of(10L, 20L, 30L),
                ClusterStatus.ACTIVE,
                List.of(SummaryStatus.GENERATED, SummaryStatus.STALE)
        )).willReturn(List.of(c20));

        MarketTrendOverview result = queryService.getOverview();

        assertThat(result.sourceClusters()).extracting(s -> s.clusterId()).containsExactly(20L);
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 빈 리스트 fallback (다른 필드는 정상 반환)")
    void getOverview_invalidJson_fallsBackToEmptyLists() {
        given(marketSnapshotRepository.findAll()).willReturn(List.of(snapshot));

        MarketTrend trend = MarketTrend.createDaily(
                LocalDate.now(), "제목", "요약",
                "{invalid json", "[not an array",
                "[broken", null);
        ReflectionTestUtils.setField(trend, "updatedAt", OffsetDateTime.now());
        given(marketTrendRepository.findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(java.time.LocalDate.now(), MarketTrend.SESSION_DAILY)).willReturn(Optional.of(trend));

        MarketTrendOverview result = queryService.getOverview();

        assertThat(result.generated()).isTrue();
        assertThat(result.title()).isEqualTo("제목");
        assertThat(result.insightCards()).isEmpty();
        assertThat(result.relatedKeywords()).isEmpty();
        assertThat(result.sourceClusters()).isEmpty();
        assertThat(result.sourceArticleCount()).isEqualTo(0);
    }

    private NewsCluster createCluster(Long id, String title, OffsetDateTime publishedAt) {
        float[] vector = {1.0f};
        NewsCluster cluster = NewsCluster.createSingle(vector, id, null, publishedAt);
        ReflectionTestUtils.setField(cluster, "id", id);
        ReflectionTestUtils.setField(cluster, "title", title);
        ReflectionTestUtils.setField(cluster, "publishedAt", publishedAt);
        return cluster;
    }
}
