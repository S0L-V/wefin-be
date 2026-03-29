package com.solv.wefin.domain.market.service;

import com.solv.wefin.domain.market.collector.MarketDataCollector;
import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import com.solv.wefin.domain.market.entity.MarketSnapshot.Unit;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketSnapshotServiceTest {

    private MarketSnapshotService marketSnapshotService;

    @Mock
    private MarketSnapshotPersistenceService persistenceService;

    @Mock
    private MarketSnapshotRepository marketSnapshotRepository;

    @Mock
    private MarketDataCollector yahooCollector;

    @Mock
    private MarketDataCollector bokCollector;

    @Test
    @DisplayName("수집 성공 — persistenceService에 저장을 위임한다")
    void collectAndSave_success() {
        // Given
        marketSnapshotService = new MarketSnapshotService(
                List.of(yahooCollector), persistenceService, marketSnapshotRepository);

        given(yahooCollector.collect()).willReturn(List.of(createNasdaqData()));
        given(yahooCollector.getSourceName()).willReturn("YahooFinance");

        // When
        marketSnapshotService.collectAndSave();

        // Then
        verify(persistenceService).saveSnapshots(anyList());
    }

    @Test
    @DisplayName("부분 실패 — 한 수집기 실패해도 다른 수집기 결과는 저장된다")
    void collectAndSave_partialFailure() {
        // Given
        marketSnapshotService = new MarketSnapshotService(
                List.of(yahooCollector, bokCollector), persistenceService, marketSnapshotRepository);

        given(yahooCollector.collect()).willThrow(new RuntimeException("Yahoo 장애"));
        given(yahooCollector.getSourceName()).willReturn("YahooFinance");
        given(bokCollector.collect()).willReturn(List.of(createBaseRateData()));
        given(bokCollector.getSourceName()).willReturn("BOK");

        // When
        marketSnapshotService.collectAndSave();

        // Then — BOK 데이터만 저장 위임
        verify(persistenceService).saveSnapshots(anyList());
    }

    @Test
    @DisplayName("전체 실패 — 모든 수집기가 실패하면 저장하지 않는다")
    void collectAndSave_allFailure() {
        // Given
        marketSnapshotService = new MarketSnapshotService(
                List.of(yahooCollector, bokCollector), persistenceService, marketSnapshotRepository);

        given(yahooCollector.collect()).willThrow(new RuntimeException("Yahoo 장애"));
        given(yahooCollector.getSourceName()).willReturn("YahooFinance");
        given(bokCollector.collect()).willThrow(new RuntimeException("BOK 장애"));
        given(bokCollector.getSourceName()).willReturn("BOK");

        // When
        marketSnapshotService.collectAndSave();

        // Then
        verify(persistenceService, never()).saveSnapshots(anyList());
    }

    @Test
    @DisplayName("빈 수집 결과 — 수집기가 빈 리스트 반환 시 저장하지 않는다")
    void collectAndSave_emptyResult() {
        // Given
        marketSnapshotService = new MarketSnapshotService(
                List.of(yahooCollector), persistenceService, marketSnapshotRepository);

        given(yahooCollector.collect()).willReturn(List.of());
        given(yahooCollector.getSourceName()).willReturn("YahooFinance");

        // When
        marketSnapshotService.collectAndSave();

        // Then
        verify(persistenceService, never()).saveSnapshots(anyList());
    }

    private CollectedMarketData createNasdaqData() {
        return CollectedMarketData.builder()
                .metricType(MetricType.NASDAQ)
                .label("나스닥")
                .value(new BigDecimal("20948.36"))
                .changeRate(new BigDecimal("-4.47"))
                .changeValue(new BigDecimal("-981.46"))
                .unit(Unit.POINT)
                .changeDirection(ChangeDirection.DOWN)
                .build();
    }

    private CollectedMarketData createBaseRateData() {
        return CollectedMarketData.builder()
                .metricType(MetricType.BASE_RATE)
                .label("한국 기준금리")
                .value(new BigDecimal("3.50"))
                .changeRate(BigDecimal.ZERO)
                .changeValue(BigDecimal.ZERO)
                .unit(Unit.PERCENT)
                .changeDirection(ChangeDirection.FLAT)
                .build();
    }
}
