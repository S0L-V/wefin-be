package com.solv.wefin.domain.news.recommendation.service;

import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import com.solv.wefin.domain.news.recommendation.repository.RecommendedNewsCardRepository;
import com.solv.wefin.domain.news.recommendation.service.NewsRecommendationService.RecommendationResult;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.CardState;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationTxServiceTest {

    @Mock private RecommendedNewsCardRepository cardRepository;
    @Mock private UserInterestRepository userInterestRepository;
    @Mock private NewsClusterRepository newsClusterRepository;
    @Mock private NewsArticleTagRepository newsArticleTagRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;

    private RecommendationTxService txService;

    private final UUID userId = UUID.randomUUID();
    private final String hash = "testhash";
    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        txService = new RecommendationTxService(
                cardRepository, userInterestRepository, newsClusterRepository, newsArticleTagRepository);
        // EntityManager는 @PersistenceContext field injection이라 reflection으로 주입
        org.springframework.test.util.ReflectionTestUtils.setField(txService, "entityManager", entityManager);
        stubAdvisoryLock();
    }

    // ── resolveGetState: 캐시 hit ────────────────────────

    @Test
    @DisplayName("GET — hash 일치 시 Cached 반환")
    void resolveGetState_hashMatch_returnsCached() {
        stubInterests("STOCK", "005930");
        stubInterests("SECTOR", "SEMICON");
        RecommendedNewsCard card = card(CardType.STOCK, "005930", now);
        when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(card));
        when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(0);
        stubUsedCodes(CardType.STOCK, "005930");
        stubUsedCodes(CardType.SECTOR, "SEMICON");

        CardState state = txService.resolveGetState(userId);

        assertThat(state).isInstanceOf(CardState.Cached.class);
    }

    // ── resolveGetState: hash 불일치 + 쿨다운 ────────────

    @Test
    @DisplayName("GET — hash 불일치 + 30분 이내 → 쿨다운 적용, 기존 카드 유지")
    void resolveGetState_hashMismatchWithinCooldown_returnsCached() {
        stubInterests("STOCK", "005930", "000660");
        stubInterests("SECTOR");
        RecommendedNewsCard card = card(CardType.STOCK, "005930", OffsetDateTime.now().minusMinutes(10));
        when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(card));
        when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(0);
        stubUsedCodes(CardType.STOCK, "005930");
        stubUsedCodes(CardType.SECTOR);

        CardState state = txService.resolveGetState(userId);

        assertThat(state).isInstanceOf(CardState.Cached.class);
        verify(cardRepository, never()).deleteByUserId(userId);
    }

    // ── resolveGetState: 세션 만료 ──────────────────────

    @Test
    @DisplayName("GET — 세션 만료된 카드가 삭제된 후 재생성 경로로 진입한다")
    void resolveGetState_expiredSession_deletesAndRegenerates() {
        when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(3);
        when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        stubInterests("STOCK");
        stubInterests("SECTOR");

        CardState state = txService.resolveGetState(userId);

        assertThat(state).isInstanceOf(CardState.Cached.class);
        verify(cardRepository).deleteExpiredCards(eq(userId), any());
    }

    // ── saveAndBuildResult: interest_code 기반 중복 방지 ──

    @Nested
    @DisplayName("saveAndBuildResult — interest_code 기반 중복 방지")
    class SaveAndBuildResultTest {

        @Test
        @DisplayName("기존 카드와 다른 interest_code면 정상 저장한다")
        void differentInterestCode_saves() {
            RecommendedNewsCard existing = card(CardType.STOCK, "005930", now);
            when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(existing))
                    .thenReturn(List.of(existing));
            stubUsedCodes(CardType.STOCK, "005930", "000660");
            stubUsedCodes(CardType.SECTOR);
            stubInterests("STOCK", "005930", "000660");
            stubInterests("SECTOR");

            RecommendedNewsCard newCard = card(CardType.STOCK, "000660", now);

            txService.saveAndBuildResult(userId, hash, List.of(newCard));

            verify(cardRepository).saveAll(argThat(cards -> {
                List<RecommendedNewsCard> list = new java.util.ArrayList<>();
                cards.forEach(list::add);
                assertThat(list).hasSize(1);
                assertThat(list.get(0).getInterestCode()).isEqualTo("000660");
                return true;
            }));
        }

        @Test
        @DisplayName("기존 카드와 같은 interest_code면 저장을 스킵한다 (동시 요청 방어)")
        void sameInterestCode_skips() {
            RecommendedNewsCard existing = card(CardType.STOCK, "005930", now);
            when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(existing));
            stubUsedCodes(CardType.STOCK, "005930");
            stubUsedCodes(CardType.SECTOR);
            stubInterests("STOCK", "005930");
            stubInterests("SECTOR");

            RecommendedNewsCard duplicate = card(CardType.STOCK, "005930", now);

            txService.saveAndBuildResult(userId, hash, List.of(duplicate));

            verify(cardRepository, never()).saveAll(anyList());
        }
    }

    // ── refresh 횟수 제한 ───────────────────────────────

    @Nested
    @DisplayName("refresh 횟수 제한")
    class RefreshLimitTest {

        @Test
        @DisplayName("refresh 5회 이후 RECOMMENDATION_REFRESH_LIMIT_EXCEEDED 발생")
        void refreshLimit_exceeded_throws() {
            when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(0);
            RecommendedNewsCard card = card(CardType.STOCK, "005930", now);
            when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(card));
            when(cardRepository.findUsedInterestCodes(userId, CardType.STOCK))
                    .thenReturn(List.of("005930", "000660", "035420", "051910", "006400", "035720"));
            when(cardRepository.findUsedInterestCodes(userId, CardType.SECTOR))
                    .thenReturn(List.of("SEMICON"));

            assertThatThrownBy(() -> txService.resolveRefreshState(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECOMMENDATION_REFRESH_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("refresh 4회까지는 정상 동작한다")
        void refreshLimit_withinLimit_proceeds() {
            when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(0);
            RecommendedNewsCard card = card(CardType.STOCK, "005930", now);
            when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(card));
            when(cardRepository.findUsedInterestCodes(userId, CardType.STOCK))
                    .thenReturn(List.of("005930", "000660", "035420", "051910", "006400"));
            when(cardRepository.findUsedInterestCodes(userId, CardType.SECTOR))
                    .thenReturn(List.of("SEMICON"));
            stubInterests("STOCK", "005930", "000660", "035420", "051910", "006400");
            stubInterests("SECTOR", "SEMICON");

            CardState state = txService.resolveRefreshState(userId);

            assertThat(state).isInstanceOf(CardState.Cached.class);
        }

        @Test
        @DisplayName("카드가 없는 상태에서는 refresh 제한 체크를 건너뛴다 — limit 초과 없이 정상 진행")
        void refreshLimit_noExistingCards_skipsCheck() {
            when(cardRepository.deleteExpiredCards(eq(userId), any())).thenReturn(0);
            when(cardRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
            stubInterests("STOCK");
            stubInterests("SECTOR");
            stubUsedCodes(CardType.STOCK);
            stubUsedCodes(CardType.SECTOR);

            CardState state = txService.resolveRefreshState(userId);

            assertThat(state).isInstanceOf(CardState.Cached.class);
        }
    }

    // ── helper ──────────────────────────────────────────

    private RecommendedNewsCard card(CardType type, String code, OffsetDateTime createdAt) {
        RecommendedNewsCard card = RecommendedNewsCard.builder()
                .userId(userId).cardType(type)
                .interestCode(code).interestName(code)
                .title("제목").summary("요약").context("맥락")
                .reasons("[]").linkedClusterId(1L)
                .interestHash(computeHashFor())
                .sessionStartedAt(now)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(card, "createdAt", createdAt);
        return card;
    }

    private String computeHashFor() {
        return hash;
    }

    private void stubAdvisoryLock() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyInt(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1L);
    }

    private void stubInterests(String type, String... codes) {
        List<UserInterest> interests = java.util.Arrays.stream(codes)
                .map(code -> UserInterest.createManual(userId, type, code, BigDecimal.valueOf(5)))
                .toList();
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, type))
                .thenReturn(interests);
    }

    private void stubUsedCodes(CardType type, String... codes) {
        when(cardRepository.findUsedInterestCodes(userId, type)).thenReturn(List.of(codes));
    }
}
