package com.solv.wefin.domain.trading.order.dto;

import java.math.BigDecimal;

import com.solv.wefin.domain.trading.order.entity.Order;

public record OrderInfo(
	Order order,
	String stockCode,
	String stockName,
	BigDecimal price,
	BigDecimal totalAmount,
	BigDecimal tax,
	BigDecimal realizedProfit,
	BigDecimal balance
) {
}
