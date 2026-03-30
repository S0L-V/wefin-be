package com.solv.wefin.domain.market.repository;

import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, Long> {

    /**
     * 지표 타입으로 스냅샷을 조회한다.
     *
     * @param metricType 조회할 지표 타입
     * @return 해당 지표의 스냅샷
     */
    Optional<MarketSnapshot> findByMetricType(MetricType metricType);
}
