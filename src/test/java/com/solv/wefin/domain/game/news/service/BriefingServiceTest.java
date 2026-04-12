package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.openai.OpenAiBriefingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

        ArgumentCaptor<BriefingCache> captor = ArgumentCaptor.forClass(BriefingCache.class);
        verify(briefingCacheRepository).save(captor.capture());
        BriefingCache savedCache = captor.getValue();
        assertThat(savedCache.getTargetDate()).isEqualTo(TEST_DATE);
        assertThat(savedCache.getBriefingText()).isEqualTo(expectedBriefing);
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
        // 폴백은 briefing_cache에 저장하지 않음
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
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 폴백 브리핑 반환 (뉴스 헤드라인 포함)
        assertThat(result).contains(TEST_DATE.toString());
        assertThat(result).contains("삼성전자 실적 발표");
        assertThat(result).contains("코스피 상승세 지속");
        // 폴백은 briefing_cache에 저장하지 않음
        verify(briefingCacheRepository, never()).save(any(BriefingCache.class));
    }

    // === 2차 방어선: 멀티 JVM 시나리오 (DataIntegrityViolationException) ===

    @Test
    @DisplayName("[2차 방어선] 다른 JVM이 먼저 저장한 경우 — UNIQUE 위반 후 기존 캐시 반환")
    void getBriefingForDate_multiJvmConcurrentSave_returnsExistingCache() {
        // Given — 단일 JVM에선 in-process 락이 막아주지만,
        //         멀티 JVM 확장 시에는 save() 시점에 UNIQUE 위반이 발생할 수 있다.
        //         이 테스트는 그 2차 방어선이 동작하는지 검증한다.
        //
        // 호출 순서:
        //   1) fast-path findByTargetDate → empty (우리 JVM 캐시 없음)
        //   2) 락 획득 후 re-check findByTargetDate → empty (아직 다른 JVM도 저장 전)
        //   3) 크롤링 + OpenAI 진행 중 다른 JVM이 먼저 save 완료
        //   4) 우리 JVM이 save → UNIQUE 위반 → catch 진입
        //   5) catch 내부 findByTargetDate → Optional.of(다른 JVM이 저장한 캐시)
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty())                                                      // (1) fast-path
                .willReturn(Optional.empty())                                                      // (2) re-check (DCL)
                .willReturn(Optional.of(BriefingCache.create(TEST_DATE, "다른 JVM이 먼저 저장한 브리핑"))); // (5) catch 내부

        List<GameNewsArchive> news = List.of(createNewsArchive("뉴스"));
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willReturn(news);

        String generatedBriefing = "내가 생성한 브리핑";
        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn(generatedBriefing);
        given(briefingCacheRepository.save(any(BriefingCache.class)))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // When
        String result = briefingService.getBriefingForDate(TEST_DATE);

        // Then — 다른 JVM이 저장한 캐시 반환
        assertThat(result).isEqualTo("다른 JVM이 먼저 저장한 브리핑");
    }

    // === 1차 방어선: 단일 JVM in-process 락 동시성 검증 ===

    @Test
    @DisplayName("[1차 방어선] 같은 날짜 동시 요청 — 크롤링/OpenAI는 1번만 호출")
    void getBriefingForDate_concurrentSameDate_onlyOneGeneration() throws Exception {
        // Given — 2개 스레드가 동시에 같은 날짜를 요청한다.
        //         첫 스레드가 크롤링 중일 때 두 번째 스레드가 진입하도록
        //         CountDownLatch로 타이밍을 정렬한다.
        final int threadCount = 2;
        final java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch insideCrawl = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger crawlCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // 캐시는 항상 empty (어떤 호출이 와도 미스)
        // → DCL 없으면 2번의 crawl/openai가 발생해야 맞지만, 락 덕분에 1번만 발생해야 한다.
        //
        // 단, 저장 이후 들어오는 재조회는 save된 것처럼 보여야 하므로
        //   - 처음 2번 (fast-path, re-check): empty
        //   - 이후: Optional.of(저장된 캐시)
        BriefingCache savedCache = BriefingCache.create(TEST_DATE, "락으로 직렬화된 브리핑");
        given(briefingCacheRepository.findByTargetDate(TEST_DATE))
                .willReturn(Optional.empty())      // thread1 fast-path
                .willReturn(Optional.empty())      // thread1 re-check
                .willReturn(Optional.empty())      // thread2 fast-path (아직 저장 전이므로 empty)
                .willReturn(Optional.of(savedCache)); // thread2 re-check — thread1이 save 완료한 시점

        // crawl 호출 시: 카운트 + 두 번째 스레드가 fast-path에 진입할 수 있도록 대기
        given(newsCrawlService.crawlAndSave(TEST_DATE)).willAnswer(inv -> {
            crawlCallCount.incrementAndGet();
            insideCrawl.countDown();                  // thread2를 깨움
            Thread.sleep(200);                         // thread2가 fast-path + 락 대기에 진입할 시간을 벌어줌
            return List.of(createNewsArchive("뉴스"));
        });

        given(openAiBriefingClient.generateBriefing(eq(TEST_DATE), anyList()))
                .willReturn("락으로 직렬화된 브리핑");

        // When — 2 스레드 동시 실행
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // thread1: 먼저 진입해서 락을 잡고 crawl로 들어감
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

        // thread2: thread1이 crawl에 진입한 이후에 시작 → 락 대기
        pool.submit(() -> {
            try {
                startGate.await();
                insideCrawl.await();   // thread1이 crawl 내부에 들어갈 때까지 대기
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

        // Then — crawl/OpenAI는 **1번만** 호출되어야 한다
        assertThat(crawlCallCount.get()).isEqualTo(1);
        verify(newsCrawlService).crawlAndSave(TEST_DATE);
        verify(openAiBriefingClient).generateBriefing(eq(TEST_DATE), anyList());
        verify(briefingCacheRepository).save(any(BriefingCache.class));
        // 두 스레드 모두 같은 브리핑 결과를 받아야 한다
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.equals("락으로 직렬화된 브리핑"));
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
