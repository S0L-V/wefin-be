package com.solv.wefin.domain.market.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 시장 지표 스냅샷 엔티티
 */
@Entity
@Table(name = "market_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_snapshot_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 30)
    private MetricType metricType;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "value", nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "change_rate", precision = 10, scale = 4)
    private BigDecimal changeRate;

    @Column(name = "change_value", precision = 18, scale = 4)
    private BigDecimal changeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_direction", nullable = false, length = 20)
    private ChangeDirection changeDirection;

    @Builder
    public MarketSnapshot(MetricType metricType, String label, BigDecimal value,
                          BigDecimal changeRate, BigDecimal changeValue,
                          Unit unit, ChangeDirection changeDirection) {
        this.metricType = metricType;
        this.label = label;
        this.value = value;
        this.changeRate = changeRate;
        this.changeValue = changeValue;
        this.unit = unit;
        this.changeDirection = changeDirection;
    }

    /**
     * 시장 지표 수치를 최신 값으로 갱신한다.
     *
     * @param value 현재 값
     * @param changeRate 전일 대비 변동률
     * @param changeValue 전일 대비 변동 값
     * @param changeDirection 변동 방향
     */
    public void updateValues(BigDecimal value, BigDecimal changeRate,
                             BigDecimal changeValue, ChangeDirection changeDirection) {
        this.value = value;
        this.changeRate = changeRate;
        this.changeValue = changeValue;
        this.changeDirection = changeDirection;
    }

    public enum MetricType {
        KOSPI, NASDAQ, BASE_RATE, USD_KRW
    }

    public enum Unit {
        POINT, PERCENT, KRW
    }

    public enum ChangeDirection {
        UP, DOWN, FLAT
    }
}
