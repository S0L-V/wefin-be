package com.solv.wefin.web.trading.order.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.order.dto.OrderInfo;
import com.solv.wefin.domain.trading.order.entity.Order;

public record OrderResponse(
	UUID orderNo,
	String stockCode,
	String stockName,
	String side,
	String orderType,
	Integer quantity,
	BigDecimal price,
	String status,
	BigDecimal totalAmount,
	BigDecimal fee,
	BigDecimal tax,
	BigDecimal realizedProfit,
	BigDecimal balance,
	OffsetDateTime createdAt
) {
	public static OrderResponse from(OrderInfo info) {
		Order order = info.order();
		return new OrderResponse(
			order.getOrderNo(),
			info.stockCode(),
			info.stockName(),
			order.getSide().name(),
			order.getOrderType().name(),
			order.getQuantity(),
			info.price(),
			order.getStatus().name(),
			info.totalAmount(),
			order.getFee(),
			info.tax(),
			info.realizedProfit(),
			info.balance(),
			order.getCreatedAt()
		);
	}
}
