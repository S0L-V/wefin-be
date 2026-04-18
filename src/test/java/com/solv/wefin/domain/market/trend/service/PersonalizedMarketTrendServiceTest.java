package com.solv.wefin.domain.market.trend.service;

import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.MarketTrendAiException;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.PersonalizedParsedCard;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.PersonalizedTrendRawResult;
import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.service.ClusterTagAggregator;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalizedMarketTrendServiceTest {

    @Mock private MarketSnapshotRepository marketSnapshotRepository;
    @Mock private UserInterestRepository userInterestRepository;
    @Mock private NewsClusterRepository newsClusterRepository;
    @Mock private NewsClusterArticleRepository clusterArticleRepository;
    @Mock private NewsArticleTagRepository newsArticleTagRepository;
    @Mock private ClusterTagAggregator tagAggregator;
    @Mock private OpenAiMarketTrendClient openAiClient;
    @Mock private MarketTrendQueryService marketTrendQueryService;
    @Mock private com.solv.wefin.domain.market.trend.repository.UserMarketTrendRepository userMarketTrendRepository;
    @Mock private UserMarketTrendCacheService userMarketTrendCacheService;
    @Spy private MarketTrendCardMapper cardMapper = new MarketTrendCardMapper();
    @Spy private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    @InjectMocks private PersonalizedMarketTrendService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("관심사 0개 — overview 폴백 + personalized=false")
    void noInterests_fallback() {
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(any(), anyString()))
                .thenReturn(List.of());
        when(marketTrendQueryService.getOverview()).thenReturn(overview());

        MarketTrendOverview result = service.getForUser(userId);

        assertThat(result.personalized()).isFalse();
        verify(openAiClient, never()).generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any());
        verify(newsClusterRepository, never())
                .findPersonalizedClusters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("매칭 클러스터 0건이지만 일반 24h 클러스터가 있으면 시장 액션 브리핑으로 생성")
    void noMatchingClusters_butGeneralAvailable_marketActionBriefing() {
        seedInterests(List.of("005930"), List.of(), List.of());
        when(newsClusterRepository.findPersonalizedClusters(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        NewsCluster general = mock(NewsCluster.class);
        when(general.getId()).thenReturn(202L);
        when(general.getTitle()).thenReturn("코스피 상승");
        when(general.getSummary()).thenReturn("요약");
        when(newsClusterRepository.findRecentActiveClusters(any(), any(), any(), any()))
                .thenReturn(List.of(general));

        NewsClusterArticle nca = mock(NewsClusterArticle.class);
        when(nca.getNewsClusterId()).thenReturn(202L);
        when(nca.getNewsArticleId()).thenReturn(8001L);
        when(clusterArticleRepository.findByNewsClusterIdIn(anyList())).thenReturn(List.of(nca));

        when(openAiClient.generateMarketActionBriefing(any(), any(), any(), any()))
                .thenReturn(rawResult(List.of(
                        validCard("c1"), validCard("c2"), validCard("c3"), validCard("c4"))));
        when(marketTrendQueryService.resolveSourceClusters(anyList())).thenReturn(List.of());
        when(marketSnapshotRepository.findAll()).thenReturn(List.of());

        MarketTrendOverview result = service.getForUser(userId);

        // ACTION_BRIEFING 모드: personalized()는 false (MATCHED만 true), mode 필드로 정확한 의미 전달
        assertThat(result.personalized()).isFalse();
        assertThat(result.mode())
                .isEqualTo(com.solv.wefin.domain.market.trend.dto.PersonalizationMode.ACTION_BRIEFING);
        verify(marketTrendQueryService, never()).getOverview();
        verify(openAiClient, never()).generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("매칭도 일반도 모두 0건 — overview 폴백")
    void noClustersAtAll_fallback() {
        seedInterests(List.of("005930"), List.of(), List.of());
        when(newsClusterRepository.findPersonalizedClusters(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(newsClusterRepository.findRecentActiveClusters(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(marketTrendQueryService.getOverview()).thenReturn(overview());

        MarketTrendOverview result = service.getForUser(userId);

        assertThat(result.personalized()).isFalse();
        verify(openAiClient, never()).generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI 호출 실패 — overview 폴백")
    void aiException_fallback() {
        seedInterests(List.of("005930"), List.of(), List.of());
        seedMatchingClusters();
        when(openAiClient.generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new MarketTrendAiException("boom", null));
        when(marketTrendQueryService.getOverview()).thenReturn(overview());

        MarketTrendOverview result = service.getForUser(userId);

        assertThat(result.personalized()).isFalse();
    }

    @Test
    @DisplayName("AI 응답 카드 중 advice/adviceLabel 누락 — overview 폴백")
    void missingAdvice_fallback() {
        seedInterests(List.of("005930"), List.of(), List.of());
        seedMatchingClusters();
        when(openAiClient.generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rawResult(List.of(
                        validCard("c1"),
                        validCard("c2"),
                        cardMissingAdvice(),
                        validCard("c4"))));
        when(marketTrendQueryService.getOverview()).thenReturn(overview());

        MarketTrendOverview result = service.getForUser(userId);

        // mapper가 invalid card를 걸러내 cards.size != 4 → 폴백
        assertThat(result.personalized()).isFalse();
    }

    @Test
    @DisplayName("정상 — personalized=true + 카드 4개 advice 모두 채워짐")
    void success_personalized() {
        seedInterests(List.of("005930"), List.of(), List.of());
        seedMatchingClusters();
        when(openAiClient.generatePersonalizedTrend(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rawResult(List.of(
                        validCard("c1"), validCard("c2"), validCard("c3"), validCard("c4"))));
        when(marketTrendQueryService.resolveSourceClusters(anyList())).thenReturn(List.of());
        when(marketSnapshotRepository.findAll()).thenReturn(List.of());

        MarketTrendOverview result = service.getForUser(userId);

        assertThat(result.personalized()).isTrue();
        assertThat(result.generated()).isTrue();
        assertThat(result.summary()).isEqualTo("맞춤 시장 분석 본문");
        assertThat(result.insightCards()).hasSize(4);
        assertThat(result.insightCards()).allSatisfy(card -> {
            assertThat(card.advice()).isNotBlank();
            assertThat(card.adviceLabel()).isIn("오늘의 제안", "투자 힌트");
            assertThat(card.relatedClusterIds()).isNotEmpty();
        });
        verify(marketTrendQueryService, never()).getOverview();
    }

    // ── helpers ───────────────────────────────────────────────

    private void seedInterests(List<String> stocks, List<String> sectors, List<String> topics) {
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "STOCK"))
                .thenReturn(toInterests(stocks, "STOCK"));
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "SECTOR"))
                .thenReturn(toInterests(sectors, "SECTOR"));
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "TOPIC"))
                .thenReturn(toInterests(topics, "TOPIC"));
    }

    private List<UserInterest> toInterests(List<String> codes, String type) {
        return codes.stream()
                .map(code -> UserInterest.createManual(userId, type, code, BigDecimal.valueOf(5)))
                .toList();
    }

    private void seedMatchingClusters() {
        NewsCluster cluster = mock(NewsCluster.class);
        when(cluster.getId()).thenReturn(101L);
        when(cluster.getTitle()).thenReturn("삼성전자 실적 호조");
        when(cluster.getSummary()).thenReturn("요약");
        when(newsClusterRepository.findPersonalizedClusters(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cluster));

        NewsClusterArticle nca = mock(NewsClusterArticle.class);
        when(nca.getNewsClusterId()).thenReturn(101L);
        when(nca.getNewsArticleId()).thenReturn(9001L);
        when(clusterArticleRepository.findByNewsClusterIdIn(anyList())).thenReturn(List.of(nca));
    }

    private PersonalizedTrendRawResult rawResult(List<PersonalizedParsedCard> cards) {
        return new PersonalizedTrendRawResult(
                "맞춤 시장 분석 본문",
                cards,
                List.of("반도체", "HBM", "AI", "기준금리", "엔비디아"));
    }

    private PersonalizedParsedCard validCard(String suffix) {
        return new PersonalizedParsedCard(
                "헤드라인 " + suffix,
                "본문 " + suffix,
                "조언 " + suffix,
                "오늘의 제안",
                List.of(1));
    }

    private PersonalizedParsedCard cardMissingAdvice() {
        return new PersonalizedParsedCard(
                "헤드라인",
                "본문",
                null,
                "오늘의 제안",
                List.of(1));
    }

    private MarketTrendOverview overview() {
        return new MarketTrendOverview(
                true,
                com.solv.wefin.domain.market.trend.dto.PersonalizationMode.OVERVIEW_FALLBACK,
                java.time.LocalDate.now(),
                "overview title", "overview summary",
                List.of(), List.of(), List.of(), 0,
                OffsetDateTime.now(),
                List.<MarketSnapshot>of());
    }
}
