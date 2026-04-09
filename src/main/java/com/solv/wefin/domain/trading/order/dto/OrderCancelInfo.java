package com.solv.wefin.domain.trading.order.dto;

import java.math.BigDecimal;

import com.solv.wefin.domain.trading.order.entity.Order;

public record OrderCancelInfo(
	Order order,
	BigDecimal refundedAmount,
	BigDecimal balance
) {
}
