package com.solv.wefin.domain.news.recommendation.service;

import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.ClusterInput;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationAiException;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationCardResult;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import com.solv.wefin.domain.news.recommendation.service.NewsRecommendationService.RecommendationResult;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.CandidateInterest;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.CardState;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.GenerationPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsRecommendationServiceTest {

    @Mock private RecommendationTxService txService;
    @Mock private OpenAiRecommendationClient aiClient;
    @Mock private RecommendationReasonBuilder reasonBuilder;
    @Mock private NewsClusterRepository newsClusterRepository;
    @InjectMocks private NewsRecommendationService service;

    private final UUID userId = UUID.randomUUID();
    private final String hash = "abc123";
    private final OffsetDateTime sessionStart = OffsetDateTime.now();

    // ── GET: 캐시 hit ───────────────────────────────────

    @Test
    @DisplayName("GET — 캐시 hit 시 AI 호출 없이 기존 카드를 반환한다")
    void get_cacheHit_returnsExistingCards() {
        RecommendationResult cached = new RecommendationResult(
                List.of(stockCard("005930")), true, 0, 5, Map.of());
        when(txService.resolveGetState(userId)).thenReturn(new CardState.Cached(cached));

        RecommendationResult result = service.getRecommendedCards(userId);

        assertThat(result.cards()).hasSize(1);
        assertThat(result.cards().get(0).getInterestCode()).isEqualTo("005930");
        verify(aiClient, never()).generate(any(), anyString(), anyString(), anyList(), anyList());
    }

    // ── GET: 첫 호출 → 카드 생성 ─────────────────────────

    @Test
    @DisplayName("GET — 첫 호출 시 AI로 STOCK + SECTOR 카드를 생성하여 저장한다")
    void get_firstCall_generatesAndSavesBothTypes() {
        GenerationPlan plan = planWith(
                List.of(candidate(CardType.STOCK, "005930", "삼성전자")),
                List.of(candidate(CardType.SECTOR, "SEMICON", "반도체")));
        when(txService.resolveGetState(userId)).thenReturn(new CardState.NeedsGeneration(plan));

        when(aiClient.generate(eq(CardType.STOCK), eq("005930"), eq("삼성전자"), anyList(), anyList()))
                .thenReturn(new RecommendationCardResult("제목1", "요약1", "맥락1", 0));
        when(aiClient.generate(eq(CardType.SECTOR), eq("SEMICON"), eq("반도체"), anyList(), anyList()))
                .thenReturn(new RecommendationCardResult("제목2", "요약2", "맥락2", 0));
        when(reasonBuilder.buildReasonsJson(any(), any(), anyString(), anyString()))
                .thenReturn("[{\"type\":\"REGISTERED_INTEREST\",\"label\":\"테스트\"}]");

        RecommendationResult saveResult = new RecommendationResult(List.of(), false, 0, 5, Map.of());
        when(txService.saveAndBuildResult(eq(userId), eq(hash), anyList())).thenReturn(saveResult);

        service.getRecommendedCards(userId);

        verify(aiClient).generate(eq(CardType.STOCK), eq("005930"), anyString(), anyList(), anyList());
        verify(aiClient).generate(eq(CardType.SECTOR), eq("SEMICON"), anyString(), anyList(), anyList());
        verify(txService).saveAndBuildResult(eq(userId), eq(hash), argThat(cards -> cards.size() == 2));
    }

    // ── AI 실패 ─────────────────────────────────────────

    @Test
    @DisplayName("STOCK AI 실패 시 SECTOR 카드만 저장한다")
    void generate_stockAiFails_savesSectorOnly() {
        GenerationPlan plan = planWith(
                List.of(candidate(CardType.STOCK, "005930", "삼성전자")),
                List.of(candidate(CardType.SECTOR, "SEMICON", "반도체")));
        when(txService.resolveGetState(userId)).thenReturn(new CardState.NeedsGeneration(plan));

        when(aiClient.generate(eq(CardType.STOCK), anyString(), anyString(), anyList(), anyList()))
                .thenThrow(new RecommendationAiException("OpenAI 오류", null));
        when(aiClient.generate(eq(CardType.SECTOR), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new RecommendationCardResult("제목", "요약", "맥락", 0));
        when(reasonBuilder.buildReasonsJson(any(), any(), anyString(), anyString())).thenReturn("[]");

        RecommendationResult saveResult = new RecommendationResult(List.of(), false, 0, 5, Map.of());
        when(txService.saveAndBuildResult(eq(userId), eq(hash), anyList())).thenReturn(saveResult);

        service.getRecommendedCards(userId);

        verify(txService).saveAndBuildResult(eq(userId), eq(hash), argThat(cards -> {
            assertThat(cards).hasSize(1);
            assertThat(cards.get(0).getCardType()).isEqualTo(CardType.SECTOR);
            return true;
        }));
    }

    @Test
    @DisplayName("양쪽 AI 모두 실패 시 새 카드 0건 → hasMore=false")
    void generate_bothAiFail_hasMoreFalse() {
        GenerationPlan plan = planWith(
                List.of(candidate(CardType.STOCK, "005930", "삼성전자")),
                List.of(candidate(CardType.SECTOR, "SEMICON", "반도체")));
        when(txService.resolveGetState(userId)).thenReturn(new CardState.NeedsGeneration(plan));

        when(aiClient.generate(any(), anyString(), anyString(), anyList(), anyList()))
                .thenThrow(new RecommendationAiException("전부 실패", null));

        RecommendationResult saveResult = new RecommendationResult(List.of(), true, 0, 5, Map.of());
        when(txService.saveAndBuildResult(eq(userId), eq(hash), anyList())).thenReturn(saveResult);

        RecommendationResult result = service.getRecommendedCards(userId);

        assertThat(result.hasMore()).isFalse();
        verify(txService).saveAndBuildResult(eq(userId), eq(hash), argThat(List::isEmpty));
    }

    // ── refresh ─────────────────────────────────────────

    @Test
    @DisplayName("refresh — 새 관심사로 카드를 교체한다")
    void refresh_generatesNewCards() {
        GenerationPlan plan = planWith(
                List.of(candidate(CardType.STOCK, "000660", "SK하이닉스")),
                List.of(candidate(CardType.SECTOR, "ENERGY", "에너지")));
        when(txService.resolveRefreshState(userId)).thenReturn(new CardState.NeedsGeneration(plan));

        when(aiClient.generate(any(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new RecommendationCardResult("제목", "요약", "맥락", 0));
        when(reasonBuilder.buildReasonsJson(any(), any(), anyString(), anyString())).thenReturn("[]");

        RecommendationResult saveResult = new RecommendationResult(List.of(), true, 0, 5, Map.of());
        when(txService.saveAndBuildResult(eq(userId), eq(hash), anyList())).thenReturn(saveResult);

        service.refreshCards(userId);

        verify(txService).saveAndBuildResult(eq(userId), eq(hash), argThat(cards -> {
            assertThat(cards).hasSize(2);
            assertThat(cards).extracting(RecommendedNewsCard::getInterestCode)
                    .containsExactlyInAnyOrder("000660", "ENERGY");
            return true;
        }));
    }

    // ── 관심사 없음 ─────────────────────────────────────

    @Test
    @DisplayName("관심사 0개 시 빈 카드 목록 + hasMore=false 반환")
    void get_noInterests_emptyResult() {
        RecommendationResult empty = new RecommendationResult(List.of(), false, 0, 5, Map.of());
        when(txService.resolveGetState(userId)).thenReturn(new CardState.Cached(empty));

        RecommendationResult result = service.getRecommendedCards(userId);

        assertThat(result.cards()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        verify(aiClient, never()).generate(any(), anyString(), anyString(), anyList(), anyList());
    }

    // ── helper ──────────────────────────────────────────

    private RecommendedNewsCard stockCard(String code) {
        return RecommendedNewsCard.builder()
                .userId(userId).cardType(CardType.STOCK)
                .interestCode(code).interestName("삼성전자")
                .title("제목").summary("요약").context("맥락")
                .reasons("[]").linkedClusterId(1L)
                .interestHash(hash).sessionStartedAt(sessionStart)
                .build();
    }

    private GenerationPlan planWith(List<CandidateInterest> stocks, List<CandidateInterest> sectors) {
        return new GenerationPlan(userId, hash, sessionStart, stocks, sectors, List.of("삼성전자", "반도체"));
    }

    private CandidateInterest candidate(CardType type, String code, String name) {
        return new CandidateInterest(type, code, name,
                List.of(new ClusterInput(1L, "클러스터 제목", "클러스터 요약")));
    }
}
