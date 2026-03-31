package com.solv.wefin.domain.market.repository;

import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, Long> {

    /**
     * 지표 타입으로 스냅샷을 조회한다.
     *
     * @param metricType 조회할 지표 타입
     * @return 해당 지표의 스냅샷
     */
    Optional<MarketSnapshot> findByMetricType(MetricType metricType);

    /**
     * 시장 지표를 race-safe하게 upsert한다.
     * PostgreSQL ON CONFLICT를 사용하여 동시 호출 시에도 unique constraint 충돌 없이 처리한다.
     */
    @Modifying
    @Query(value = "INSERT INTO market_snapshot (metric_type, label, value, change_rate, change_value, unit, change_direction, created_at, updated_at) " +
            "VALUES (:metricType, :label, :value, :changeRate, :changeValue, :unit, :changeDirection, now(), now()) " +
            "ON CONFLICT (metric_type) DO UPDATE SET " +
            "value = :value, change_rate = :changeRate, change_value = :changeValue, " +
            "change_direction = :changeDirection, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("metricType") String metricType,
                @Param("label") String label,
                @Param("value") BigDecimal value,
                @Param("changeRate") BigDecimal changeRate,
                @Param("changeValue") BigDecimal changeValue,
                @Param("unit") String unit,
                @Param("changeDirection") String changeDirection);
}
