package com.solv.wefin.domain.interest.service;

import com.solv.wefin.domain.interest.dto.InterestInfo;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestServiceTest {

    @Mock private UserInterestRepository userInterestRepository;
    @Mock private NewsArticleTagRepository newsArticleTagRepository;
    @Mock private ManualInterestLockService manualInterestLockService;
    @InjectMocks private InterestService interestService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("SECTOR 관심사 목록 — 한 번의 batch 쿼리로 표시명을 채워 반환한다")
    void list_sector_resolvesDisplayName() {
        UserInterest saved = UserInterest.createManual(userId, "SECTOR", "SEMICON", BigDecimal.valueOf(5));
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "SECTOR"))
                .thenReturn(List.of(saved));
        when(newsArticleTagRepository.findTagNamesByTagTypeAndTagCodes("SECTOR", List.of("SEMICON")))
                .thenReturn(List.of(stubProjection("SEMICON", "반도체")));

        List<InterestInfo> result = interestService.list(userId, InterestType.SECTOR);

        assertThat(result).containsExactly(new InterestInfo("SEMICON", "반도체"));
    }

    @Test
    @DisplayName("list — 현재 표시명이 없으면 code로 fallback")
    void list_fallbackToCode_whenNameMissing() {
        UserInterest saved = UserInterest.createManual(userId, "TOPIC", "AI", BigDecimal.valueOf(5));
        when(userInterestRepository.findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "TOPIC"))
                .thenReturn(List.of(saved));
        when(newsArticleTagRepository.findTagNamesByTagTypeAndTagCodes("TOPIC", List.of("AI")))
                .thenReturn(List.of());

        List<InterestInfo> result = interestService.list(userId, InterestType.TOPIC);

        assertThat(result).containsExactly(new InterestInfo("AI", "AI"));
    }

    private NewsArticleTagRepository.TagNameProjection stubProjection(String code, String name) {
        return new NewsArticleTagRepository.TagNameProjection() {
            @Override public String getCode() { return code; }
            @Override public String getName() { return name; }
        };
    }

    @Test
    @DisplayName("add — AI가 부여한 태그가 아니면 INTEREST_TAG_NOT_FOUND")
    void add_invalidTag_throws() {
        when(newsArticleTagRepository.existsByTagTypeAndTagCode(NewsArticleTag.TagType.SECTOR, "NOPE"))
                .thenReturn(false);

        assertThatThrownBy(() -> interestService.add(userId, InterestType.SECTOR, "NOPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTEREST_TAG_NOT_FOUND);

        verify(userInterestRepository, never()).save(any());
    }

    @Test
    @DisplayName("add — 이미 등록된 경우 INTEREST_ALREADY_EXISTS")
    void add_duplicate_throws() {
        when(newsArticleTagRepository.existsByTagTypeAndTagCode(NewsArticleTag.TagType.SECTOR, "SEMICON"))
                .thenReturn(true);
        when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(userId, "SECTOR", "SEMICON"))
                .thenReturn(true);

        assertThatThrownBy(() -> interestService.add(userId, InterestType.SECTOR, "SEMICON"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTEREST_ALREADY_EXISTS);

        verify(userInterestRepository, never()).save(any());
    }

    @Test
    @DisplayName("add — 타입별 10개 한도 초과 시 INTEREST_LIMIT_EXCEEDED")
    void add_exceedsLimit_throws() {
        when(newsArticleTagRepository.existsByTagTypeAndTagCode(NewsArticleTag.TagType.TOPIC, "AI"))
                .thenReturn(true);
        when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(userId, "TOPIC", "AI"))
                .thenReturn(false);
        when(userInterestRepository.countByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "TOPIC"))
                .thenReturn(10L);

        assertThatThrownBy(() -> interestService.add(userId, InterestType.TOPIC, "AI"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTEREST_LIMIT_EXCEEDED);

        verify(userInterestRepository, never()).save(any());
    }

    @Test
    @DisplayName("add — 유효 태그 + 미등록 + 한도 미달이면 가중치 +5로 저장")
    void add_valid_saves() {
        when(newsArticleTagRepository.existsByTagTypeAndTagCode(NewsArticleTag.TagType.SECTOR, "SEMICON"))
                .thenReturn(true);
        when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(userId, "SECTOR", "SEMICON"))
                .thenReturn(false);
        when(userInterestRepository.countByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "SECTOR"))
                .thenReturn(3L);

        interestService.add(userId, InterestType.SECTOR, "SEMICON");

        ArgumentCaptor<UserInterest> captor = ArgumentCaptor.forClass(UserInterest.class);
        verify(userInterestRepository).save(captor.capture());
        UserInterest saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getInterestType()).isEqualTo("SECTOR");
        assertThat(saved.getInterestValue()).isEqualTo("SEMICON");
        assertThat(saved.getWeight()).isEqualByComparingTo(InterestService.ADD_WEIGHT);
    }

    @Test
    @DisplayName("delete — 수동 등록 row만 삭제, 피드백 가중치(upsertWeight) row는 건드리지 않음")
    void delete_removesManualRowOnly() {
        interestService.delete(userId, InterestType.SECTOR, "SEMICON");

        verify(userInterestRepository).deleteByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                userId, "SECTOR", "SEMICON");
        verify(userInterestRepository, never()).upsertWeight(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("STOCK 타입은 Watchlist가 담당 — InterestService는 INVALID_INPUT으로 차단")
    void add_rejectsStockType() {
        assertThatThrownBy(() -> interestService.add(userId, InterestType.STOCK, "005930"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verify(newsArticleTagRepository, never()).existsByTagTypeAndTagCode(any(), anyString());
    }
}
