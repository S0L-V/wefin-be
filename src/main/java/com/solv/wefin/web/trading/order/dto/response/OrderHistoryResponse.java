package com.solv.wefin.web.trading.order.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.order.entity.Order;

public record OrderHistoryResponse(
	Long orderId,
	UUID orderNo,
	String stockCode,
	String stockName,
	String side,
	String orderType,
	Integer quantity,
	BigDecimal requestPrice,
	String status,
	BigDecimal fee,
	BigDecimal tax,
	OffsetDateTime createdAt
) {
	public static OrderHistoryResponse from(Order order, String stockCode, String stockName) {
		return new OrderHistoryResponse(
			order.getOrderId(),
			order.getOrderNo(),
			stockCode,
			stockName,
			order.getSide().name(),
			order.getOrderType().name(),
			order.getQuantity(),
			order.getRequestPrice(),
			order.getStatus().name(),
			order.getFee(),
			order.getTax(),
			order.getCreatedAt()
		);
	}
}
