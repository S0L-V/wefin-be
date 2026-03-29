package com.solv.wefin.domain.market.repository;

import com.solv.wefin.common.IntegrationTestBase;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import com.solv.wefin.domain.market.entity.MarketSnapshot.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketSnapshotRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MarketSnapshotRepository marketSnapshotRepository;

    @Test
    @DisplayName("MarketSnapshot INSERT — 신규 지표가 정상 저장된다")
    void insert_snapshot() {
        // Given
        MarketSnapshot snapshot = MarketSnapshot.builder()
                .metricType(MetricType.NASDAQ)
                .label("나스닥")
                .value(new BigDecimal("20948.3600"))
                .changeRate(new BigDecimal("-4.47"))
                .changeValue(new BigDecimal("-981.4600"))
                .unit(Unit.POINT)
                .changeDirection(ChangeDirection.DOWN)
                .build();

        // When
        marketSnapshotRepository.save(snapshot);

        // Then
        Optional<MarketSnapshot> found = marketSnapshotRepository.findByMetricType(MetricType.NASDAQ);
        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualByComparingTo("20948.36");
        assertThat(found.get().getLabel()).isEqualTo("나스닥");
        assertThat(found.get().getChangeDirection()).isEqualTo(ChangeDirection.DOWN);
    }

    @Test
    @DisplayName("MarketSnapshot UPDATE — 기존 지표가 최신 값으로 갱신된다")
    void upsert_snapshot() {
        // Given — 기존 데이터 저장
        MarketSnapshot snapshot = MarketSnapshot.builder()
                .metricType(MetricType.USD_KRW)
                .label("원/달러 환율")
                .value(new BigDecimal("1500.0000"))
                .changeRate(new BigDecimal("0.50"))
                .changeValue(new BigDecimal("7.0000"))
                .unit(Unit.KRW)
                .changeDirection(ChangeDirection.UP)
                .build();
        marketSnapshotRepository.save(snapshot);

        // When — 값 갱신
        MarketSnapshot existing = marketSnapshotRepository
                .findByMetricType(MetricType.USD_KRW).orElseThrow();
        existing.updateValues(
                new BigDecimal("1508.0000"),
                new BigDecimal("1.03"),
                new BigDecimal("15.0000"),
                ChangeDirection.UP);
        marketSnapshotRepository.flush();

        // Then
        MarketSnapshot updated = marketSnapshotRepository
                .findByMetricType(MetricType.USD_KRW).orElseThrow();
        assertThat(updated.getValue()).isEqualByComparingTo("1508.00");
        assertThat(updated.getChangeRate()).isEqualByComparingTo("1.03");
        assertThat(updated.getChangeValue()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("metric_type unique 제약 — 동일 지표 타입 중복 INSERT 시 예외 발생")
    void unique_constraint() {
        // Given
        MarketSnapshot first = MarketSnapshot.builder()
                .metricType(MetricType.KOSPI)
                .label("코스피")
                .value(new BigDecimal("2700.0000"))
                .changeRate(BigDecimal.ZERO)
                .changeValue(BigDecimal.ZERO)
                .unit(Unit.POINT)
                .changeDirection(ChangeDirection.FLAT)
                .build();
        marketSnapshotRepository.save(first);
        marketSnapshotRepository.flush();

        // When & Then
        MarketSnapshot duplicate = MarketSnapshot.builder()
                .metricType(MetricType.KOSPI)
                .label("코스피")
                .value(new BigDecimal("2750.0000"))
                .changeRate(BigDecimal.ZERO)
                .changeValue(BigDecimal.ZERO)
                .unit(Unit.POINT)
                .changeDirection(ChangeDirection.FLAT)
                .build();

        assertThatThrownBy(() -> {
            marketSnapshotRepository.save(duplicate);
            marketSnapshotRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
