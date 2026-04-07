package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.news.service.BriefingService;
import com.solv.wefin.domain.game.news.service.NewsBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewsBatchServiceTest {

    @InjectMocks
    private NewsBatchService newsBatchService;

    @Mock
    private BriefingService briefingService;

    @Mock
    private BriefingCacheRepository briefingCacheRepository;

    private static final LocalDate COLLECT_START = LocalDate.of(2020, 1, 2);
    private static final LocalDate COLLECT_END = LocalDate.of(2024, 12, 31);

    // === 배치 수집 성공 테스트 ===

    @Test
    @DisplayName("배치 수집 성공 — 미처리 날짜만 처리한다")
    void collectBatch_success_processesUnprocessedDates() {
        // Given — 1/2, 1/3은 이미 처리됨, 1/4, 1/5는 미처리
        Set<LocalDate> existingDates = new HashSet<>();
        existingDates.add(LocalDate.of(2020, 1, 2));
        existingDates.add(LocalDate.of(2020, 1, 3));

        given(briefingCacheRepository.findExistingDatesBetween(COLLECT_START, COLLECT_END))
                .willReturn(existingDates);
        given(briefingService.getBriefingForDate(any(LocalDate.class)))
                .willReturn("브리핑 텍스트");

        // When — 3일치 배치 처리 요청
        int result = newsBatchService.collectBatch(3);

        // Then — 미처리 3건 처리 (1/4, 1/5, 1/6)
        assertThat(result).isEqualTo(3);
        verify(briefingService).getBriefingForDate(LocalDate.of(2020, 1, 4));
        verify(briefingService).getBriefingForDate(LocalDate.of(2020, 1, 5));
        verify(briefingService).getBriefingForDate(LocalDate.of(2020, 1, 6));
        // 이미 처리된 날짜는 호출되지 않음
        verify(briefingService, never()).getBriefingForDate(LocalDate.of(2020, 1, 2));
        verify(briefingService, never()).getBriefingForDate(LocalDate.of(2020, 1, 3));
    }

    // === 이미 처리된 날짜 스킵 테스트 ===

    @Test
    @DisplayName("이미 처리된 날짜 스킵 — 처리된 날짜 사이의 미처리만 처리")
    void collectBatch_skipsProcessedDates() {
        // Given — 1/2는 처리됨, 1/3은 미처리, 1/4는 처리됨, 1/5는 미처리
        Set<LocalDate> existingDates = new HashSet<>();
        existingDates.add(LocalDate.of(2020, 1, 2));
        existingDates.add(LocalDate.of(2020, 1, 4));

        given(briefingCacheRepository.findExistingDatesBetween(COLLECT_START, COLLECT_END))
                .willReturn(existingDates);
        given(briefingService.getBriefingForDate(any(LocalDate.class)))
                .willReturn("브리핑 텍스트");

        // When — 2일치만 처리 요청
        int result = newsBatchService.collectBatch(2);

        // Then — 미처리 2건 처리 (1/3, 1/5)
        assertThat(result).isEqualTo(2);
        verify(briefingService).getBriefingForDate(LocalDate.of(2020, 1, 3));
        verify(briefingService).getBriefingForDate(LocalDate.of(2020, 1, 5));
    }

    // === 모든 날짜 처리 완료 ===

    @Test
    @DisplayName("모든 날짜 처리 완료 — 미처리 날짜 없으면 0 반환")
    void collectBatch_allProcessed_returnsZero() {
        // Given — 전체 기간의 모든 날짜가 이미 처리됨
        Set<LocalDate> allDates = new HashSet<>();
        LocalDate current = COLLECT_START;
        while (!current.isAfter(COLLECT_END)) {
            allDates.add(current);
            current = current.plusDays(1);
        }

        given(briefingCacheRepository.findExistingDatesBetween(COLLECT_START, COLLECT_END))
                .willReturn(allDates);

        // When
        int result = newsBatchService.collectBatch(150);

        // Then — 0 반환, briefingService 호출 없음
        assertThat(result).isEqualTo(0);
        verify(briefingService, never()).getBriefingForDate(any());
    }

    // === 동시 배치 실행 방지 (AtomicBoolean) ===

    @Test
    @DisplayName("동시 배치 실행 방지 — 이미 실행 중이면 0 반환")
    void collectBatch_concurrentExecution_returnsZero() throws Exception {
        // Given — 첫 번째 배치가 오래 걸리는 상황 시뮬레이션
        Set<LocalDate> existingDates = new HashSet<>();
        given(briefingCacheRepository.findExistingDatesBetween(COLLECT_START, COLLECT_END))
                .willReturn(existingDates);

        // 첫 번째 호출은 느리게 처리되도록 설정
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCallToFinish = new CountDownLatch(1);

        given(briefingService.getBriefingForDate(any(LocalDate.class)))
                .willAnswer(invocation -> {
                    firstCallStarted.countDown();
                    allowFirstCallToFinish.await();
                    return "브리핑";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger firstResult = new AtomicInteger(-1);
        AtomicInteger secondResult = new AtomicInteger(-1);

        // When — 두 스레드가 동시에 배치 실행
        Future<?> first = executor.submit(() -> {
            firstResult.set(newsBatchService.collectBatch(1));
        });

        // 첫 번째 배치가 시작될 때까지 대기
        firstCallStarted.await();

        // 두 번째 배치 시도 (running == true이므로 즉시 0 반환)
        Future<?> second = executor.submit(() -> {
            secondResult.set(newsBatchService.collectBatch(1));
        });

        second.get(); // 두 번째는 즉시 완료

        // Then — 두 번째 배치는 0 반환 (스킵)
        assertThat(secondResult.get()).isEqualTo(0);

        // 첫 번째 배치 완료 허용
        allowFirstCallToFinish.countDown();
        first.get();

        // 첫 번째 배치는 정상 처리
        assertThat(firstResult.get()).isEqualTo(1);

        executor.shutdown();
    }

    // === 일부 날짜 실패 시 나머지 계속 처리 ===

    @Test
    @DisplayName("일부 날짜 실패 — 실패해도 나머지 날짜는 계속 처리한다")
    void collectBatch_partialFailure_continuesProcessing() {
        // Given — 3일치 중 2번째 날짜에서 예외 발생
        Set<LocalDate> existingDates = new HashSet<>();
        given(briefingCacheRepository.findExistingDatesBetween(COLLECT_START, COLLECT_END))
                .willReturn(existingDates);

        LocalDate day1 = LocalDate.of(2020, 1, 2);
        LocalDate day2 = LocalDate.of(2020, 1, 3);
        LocalDate day3 = LocalDate.of(2020, 1, 4);

        given(briefingService.getBriefingForDate(day1)).willReturn("브리핑1");
        given(briefingService.getBriefingForDate(day2))
                .willThrow(new RuntimeException("크롤링 실패"));
        given(briefingService.getBriefingForDate(day3)).willReturn("브리핑3");

        // When
        int result = newsBatchService.collectBatch(3);

        // Then — 성공 2건 반환 (실패 1건은 카운트 안 됨)
        assertThat(result).isEqualTo(2);
        // 3건 모두 호출됨 (실패해도 다음 날짜 계속 처리)
        verify(briefingService).getBriefingForDate(day1);
        verify(briefingService).getBriefingForDate(day2);
        verify(briefingService).getBriefingForDate(day3);
    }
}
