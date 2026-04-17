package com.solv.wefin.domain.trading.indices.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지수 현재값 + 스파크라인 묶음 (도메인 DTO)
 */
public record IndexQuote(
    IndexCode code,
    String label,
    BigDecimal currentValue,
    BigDecimal changeValue,
    BigDecimal changeRate,
    ChangeDirection changeDirection,
    boolean isDelayed,
    MarketStatus marketStatus,
    List<SparklinePoint> sparkline
) {}
