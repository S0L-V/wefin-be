package com.solv.wefin.web.market.dto.response;

import com.solv.wefin.domain.market.entity.MarketSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class MarketSnapshotResponse {

    private final String metricType;
    private final String label;
    private final BigDecimal value;
    private final BigDecimal changeRate;
    private final BigDecimal changeValue;
    private final String unit;
    private final String changeDirection;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static MarketSnapshotResponse from(MarketSnapshot snapshot) {
        return MarketSnapshotResponse.builder()
                .metricType(snapshot.getMetricType().name())
                .label(snapshot.getLabel())
                .value(snapshot.getValue())
                .changeRate(snapshot.getChangeRate())
                .changeValue(snapshot.getChangeValue())
                .unit(snapshot.getUnit().name())
                .changeDirection(snapshot.getChangeDirection().name())
                .createdAt(snapshot.getCreatedAt())
                .updatedAt(snapshot.getUpdatedAt())
                .build();
    }
}
