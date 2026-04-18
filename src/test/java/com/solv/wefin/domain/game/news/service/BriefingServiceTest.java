package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient.BriefingParts;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

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
        BriefingCache cached = BriefingCache.create(
                TEST_DATE, "캐시된 시장 개요", "캐시된 주요 이슈", "캐시된 투자 힌트");
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.of(cached));

        // When
        BriefingParts result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 캐시 데이터 반환, 크롤링/OpenAI 미호출
        assertThat(result.marketOverview()).isEqualTo("캐시된 시장 개요");
        assertThat(result.keyIssues()).isEqualTo("캐시된 주요 이슈");
        assertThat(result.investmentHint()).isEqualTo("캐시된 투자 힌트");
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

        BriefingParts expectedParts = new BriefingParts(
                "반도체 호황과 금융 규제가 주요 이슈...",
                "- 반도체 섹터\n- 금융 섹터\n- IT 섹터",
                "반도체 관련 종목에 주목하세요."
        );
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(expectedParts);

        // When
        BriefingParts result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — OpenAI 브리핑 반환, 캐시 저장 확인
        assertThat(result.marketOverview()).isEqualTo(expectedParts.marketOverview());
        assertThat(result.keyIssues()).isEqualTo(expectedParts.keyIssues());
        assertThat(result.investmentHint()).isEqualTo(expectedParts.investmentHint());
        verify(newsCrawlService).crawlAndSave(TEST_DATE);
        verify(openAiBriefingClient).generateBriefing(eq(TEST_DATE), anyList());

        ArgumentCaptor<BriefingCache> captor = ArgumentCaptor.forClass(BriefingCache.class);
        verify(briefingCacheRepository).save(captor.capture());
        BriefingCache savedCache = captor.getValue();
        assertThat(savedCache.getTargetDate()).isEqualTo(TEST_DATE);
        assertThat(savedCache.getMarketOverview()).isEqualTo(expectedParts.marketOverview());
        assertThat(savedCache.getKeyIssues()).isEqualTo(expectedParts.keyIssues());
        assertThat(savedCache.getInvestmentHint()).isEqualTo(expectedParts.investmentHint());
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
        BriefingParts result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 기본 브리핑 텍스트 반환, OpenAI 미호출
        assertThat(result.marketOverview()).contains(TEST_DATE.toString());
        assertThat(result.marketOverview()).contains("뉴스 데이터가 없습니다");
        verify(openAiBriefingClient, never()).generateBriefing(any(), anyList());
        verify(briefingCacheRepository, never()).save(any(BriefingCache.class));
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
        BriefingParts result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 폴백 브리핑 반환 (뉴스 헤드라인 포함)
        assertThat(result.marketOverview()).contains(TEST_DATE.toString());
        assertThat(result.keyIssues()).contains("삼성전자 실적 발표");
        assertThat(result.keyIssues()).contains("코스피 상승세 지속");
        verify(briefingCacheRepository, never()).save(any(BriefingCache.class));
    }

    // === 2차 방어선: 멀티 JVM 시나리오 (DataIntegrityViolationException) ===

    @Test
    @DisplayName("[2차 방어선] 다른 JVM이 먼저 저장한 경우 — UNIQUE 위반 후 기존 캐시 반환")
    void getBriefingForDate_multiJvmConcurrentSave_returnsExistingCache() {
        BriefingCache otherJvmCache = BriefingCache.create(
                TEST_DATE, "다른 JVM 시장 개요", "다른 JVM 이슈", "다른 JVM 힌트");
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty())
                .willReturn(Optional.empty())
                .willReturn(Optional.of(otherJvmCache));

        List<GameNewsArchive> news = List.of(createNewsArchive("뉴스"));
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willReturn(news);

        BriefingParts generatedParts = new BriefingParts("내 시장 개요", "내 이슈", "내 힌트");
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(generatedParts);
        given(briefingCacheRepository.save(any(BriefingCache.class)))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // When
        BriefingParts result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 다른 JVM이 저장한 캐시 반환
        assertThat(result.marketOverview()).isEqualTo("다른 JVM 시장 개요");
        assertThat(result.keyIssues()).isEqualTo("다른 JVM 이슈");
        assertThat(result.investmentHint()).isEqualTo("다른 JVM 힌트");
    }

    // === 1차 방어선: 단일 JVM in-process 락 동시성 검증 ===

    @Test
    @DisplayName("[1차 방어선] 같은 날짜 동시 요청 — 크롤링/OpenAI는 1번만 호출")
    void getBriefingForDate_concurrentSameDate_onlyOneGeneration() throws Exception {
        final int threadCount = 2;
        final java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch insideCrawl = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger crawlCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        BriefingCache savedCache = BriefingCache.create(
                TEST_DATE, "락으로 직렬화된 시장 개요", "락으로 직렬화된 이슈", "락으로 직렬화된 힌트");
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty())      // thread1 fast-path
                .willReturn(Optional.empty())      // thread1 re-check
                .willReturn(Optional.empty())      // thread2 fast-path
                .willReturn(Optional.of(savedCache)); // thread2 re-check

        given(newsCrawlService.crawlAndSave(TEST_DATE)).willAnswer(inv -> {
            crawlCallCount.incrementAndGet();
            insideCrawl.countDown();
            Thread.sleep(200);
            return List.of(createNewsArchive("뉴스"));
        });

        BriefingParts generatedParts = new BriefingParts(
                "락으로 직렬화된 시장 개요", "락으로 직렬화된 이슈", "락으로 직렬화된 힌트");
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(generatedParts);

        // When
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.List<BriefingParts> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        pool.submit(() -> {
            try {
                startGate.await();
                results.add(briefingService.getBriefingForDate(TEST_DATE));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        pool.submit(() -> {
            try {
                startGate.await();
                insideCrawl.await();
                results.add(briefingService.getBriefingForDate(TEST_DATE));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        startGate.countDown();
        done.await(5, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        // Then
        assertThat(crawlCallCount.get()).isEqualTo(1);
        verify(newsCrawlService).crawlAndSave(TEST_DATE);
        verify(openAiBriefingClient).generateBriefing(eq(TEST_DATE), anyList());
        verify(briefingCacheRepository).save(any(BriefingCache.class));
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> p.marketOverview().equals("락으로 직렬화된 시장 개요"));
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
