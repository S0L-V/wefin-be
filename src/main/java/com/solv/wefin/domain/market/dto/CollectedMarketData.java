package com.solv.wefin.domain.market.dto;

import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import com.solv.wefin.domain.market.entity.MarketSnapshot.Unit;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 시장 지표 데이터 DTO
 */
@Getter
@Builder
public class CollectedMarketData {

    private final MetricType metricType; // 지표 타입
    private final String label; // 화면 표시 이름
    private final BigDecimal value; // 현재 값
    private final BigDecimal changeRate; // 전일 대비 변동률
    private final BigDecimal changeValue; // 전일 대비 변동 값
    private final Unit unit; // 단위
    private final ChangeDirection changeDirection; // 변동 방향
}
