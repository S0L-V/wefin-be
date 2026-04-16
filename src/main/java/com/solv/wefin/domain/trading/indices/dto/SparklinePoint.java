package com.solv.wefin.domain.trading.indices.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 스파크라인 단일 포인트.
 *
 * @param t 시점 (UTC Instant, 프론트에서 KST 변환)
 * @param v 해당 시점 지수 값
 */
public record SparklinePoint(Instant t, BigDecimal v) {}
