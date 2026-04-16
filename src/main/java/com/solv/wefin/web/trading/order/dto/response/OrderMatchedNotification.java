package com.solv.wefin.web.trading.order.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.matching.event.OrderMatchedEvent;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderType;

public record OrderMatchedNotification(
	UUID orderNo,
	String stockCode,
	String stockName,
	OrderSide side,
	OrderType orderType,
	Integer quantity,
	BigDecimal price,
	BigDecimal fee,
	BigDecimal tax,
	BigDecimal realizedProfit,
	BigDecimal balance,
	OffsetDateTime matchedAt
) {
	public static OrderMatchedNotification from(OrderMatchedEvent event) {
		return new OrderMatchedNotification(
			event.orderNo(),
			event.stockCode(),
			event.stockName(),
			event.side(),
			event.orderType(),
			event.quantity(),
			event.price(),
			event.fee(),
			event.tax(),
			event.realizedProfit(),
			event.balance(),
			event.matchedAt()
		);
	}
}
