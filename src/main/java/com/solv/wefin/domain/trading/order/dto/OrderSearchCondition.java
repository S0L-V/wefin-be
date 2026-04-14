package com.solv.wefin.domain.trading.order.dto;

import java.time.LocalDate;

import com.solv.wefin.domain.trading.order.entity.OrderStatus;

public record OrderSearchCondition(
	OrderStatus status,
	Long stockId,
	LocalDate startDate,
	LocalDate endDate
) {
}
