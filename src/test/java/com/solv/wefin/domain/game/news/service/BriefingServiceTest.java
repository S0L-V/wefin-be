package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.news.service.BriefingService;
import com.solv.wefin.domain.game.news.service.NewsCrawlService;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BriefingServiceTest {

    @InjectMocks
    private BriefingService briefingService;

    @Mock
    private NewsCrawlService newsCrawlService;

    @Mock
    private OpenAiBriefingClient openAiBriefingClient;

    @Mock
    private BriefingCacheRepository briefingCacheRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2020, 6, 15);
    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    // === 캐시 히트 테스트 ===

    @Test
    @DisplayName("캐시 히트 — 크롤링/OpenAI 호출 없이 기존 브리핑 반환")
    void getBriefingForDate_cacheHit() {
        // Given — 캐시에 브리핑이 이미 존재
        BriefingCache cached = BriefingCache.create(TEST_DATE, "캐시된 브리핑 텍스트");
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.of(cached));

        // When
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 캐시 데이터 반환, 크롤링/OpenAI 미호출
        assertThat(result).isEqualTo("캐시된 브리핑 텍스트");
        verify(newsCrawlService, never()).crawlAndSave(any());
        verify(openAiBriefingClient, never()).generateBriefing(any(), anyList());
    }

    // === 캐시 미스 → 생성 흐름 테스트 ===

    @Test
    @DisplayName("캐시 미스 — 크롤링 → OpenAI → 캐시 저장 → 브리핑 반환")
    void getBriefingForDate_cacheMiss_generatesAndCaches() {
        // Given — 캐시 없음, 뉴스 2건 크롤링, OpenAI 브리핑 생성 성공
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty());

        List<GameNewsArchive> news = List.of(
                createNewsArchive("반도체 호황"),
                createNewsArchive("금융 규제 강화")
        );
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willReturn(news);

        String expectedBriefing = "[시장 개요] 반도체 호황과 금융 규제가 주요 이슈...";
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(expectedBriefing);

        // When
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — OpenAI 브리핑 반환, 캐시 저장 확인
        assertThat(result).isEqualTo(expectedBriefing);
        verify(newsCrawlService).crawlAndSave(TEST_DATE);
        verify(openAiBriefingClient).generateBriefing(eq(TEST_DATE), anyList());
        verify(briefingCacheRepository).save(any(BriefingCache.class));
    }

    // === 뉴스 없을 때 기본 브리핑 ===

    @Test
    @DisplayName("뉴스 없을 때 — 기본 브리핑 텍스트 반환")
    void getBriefingForDate_noNews_returnsDefaultBriefing() {
        // Given — 캐시 없음, 크롤링 결과 빈 리스트
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty());
        given(newsCrawlService.crawlAndSave(TEST_DATE))
                .willReturn(Collections.emptyList());

        // When
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 기본 브리핑 텍스트 반환, OpenAI 미호출
        assertThat(result).contains(TEST_DATE.toString());
        assertThat(result).contains("뉴스 데이터가 없습니다");
        verify(openAiBriefingClient, never()).generateBriefing(any(), anyList());
        verify(briefingCacheRepository).save(any(BriefingCache.class));
    }

    // === OpenAI 호출 실패 시 폴백 ===

    @Test
    @DisplayName("OpenAI 호출 실패 — 뉴스 헤드라인 기반 폴백 브리핑 반환")
    void getBriefingForDate_openAiFailure_returnsFallback() {
        // Given — 캐시 없음, 뉴스 2건 있지만 OpenAI 호출 실패
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty());

        List<GameNewsArchive> news = List.of(
                createNewsArchive("삼성전자 실적 발표"),
                createNewsArchive("코스피 상승세 지속")
        );
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willReturn(news);
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willThrow(new RuntimeException("OpenAI API 타임아웃"));

        // When
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 폴백 브리핑 반환 (뉴스 헤드라인 포함)
        assertThat(result).contains(TEST_DATE.toString());
        assertThat(result).contains("삼성전자 실적 발표");
        assertThat(result).contains("코스피 상승세 지속");
        // 폴백이어도 캐시에 저장됨
        verify(briefingCacheRepository).save(any(BriefingCache.class));
    }

    // === 동시 요청 시 DataIntegrityViolationException 처리 ===

    @Test
    @DisplayName("동시 요청 시 UNIQUE 위반 — 기존 캐시 데이터 반환")
    void getBriefingForDate_concurrentSave_returnsExistingCache() {
        // Given — 캐시 없음 → 브리핑 생성 → 캐시 저장 시 UNIQUE 위반
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty())  // 첫 조회: 없음
                .willReturn(Optional.of(BriefingCache.create(TEST_DATE, "다른 스레드가 먼저 저장한 브리핑")));  // 예외 후 재조회

        List<GameNewsArchive> news = List.of(createNewsArchive("뉴스"));
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willReturn(news);

        String generatedBriefing = "내가 생성한 브리핑";
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(generatedBriefing);
        given(briefingCacheRepository.save(any(BriefingCache.class)))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // When — 동시 저장 예외 발생
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 다른 스레드가 먼저 저장한 캐시 반환
        assertThat(result).isEqualTo("다른 스레드가 먼저 저장한 브리핑");
    }

    // === 헬퍼 메서드 ===

    private GameNewsArchive createNewsArchive(String title) {
        return GameNewsArchive.create(
                title,
                title + " 요약 내용",
                "naver_finance",
                "https://news.com/" + title.hashCode(),
                TEST_DATE.atTime(9, 0).atOffset(KST),
                "반도체",
                "반도체"
        );
    }
}
