package com.solv.wefin.domain.trading.order.dto;

import java.time.LocalDate;
import java.util.List;

import com.solv.wefin.domain.trading.order.entity.OrderStatus;

public record OrderSearchCondition(
	List<OrderStatus> statuses,
	Long stockId,
	LocalDate startDate,
	LocalDate endDate
) {
}
