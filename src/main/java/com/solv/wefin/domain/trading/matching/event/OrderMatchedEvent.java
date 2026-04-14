package com.solv.wefin.domain.trading.matching.event;

import java.math.BigDecimal;
import java.util.UUID;

import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderType;

public record OrderMatchedEvent(
	OrderType orderType,
	UUID orderNo,
	String stockCode,
	String stockName,
	OrderSide side,
	Integer quantity,
	BigDecimal price,
	BigDecimal fee,
	BigDecimal tax,
	BigDecimal realizedProfit,
	BigDecimal balance
) {
	public static OrderMatchedEvent ofBuy(OrderType orderType, UUID orderNo, String stockCode,
										  String stockName, Integer quantity,
										  BigDecimal price, BigDecimal fee, BigDecimal balance) {
		return new OrderMatchedEvent(orderType, orderNo, stockCode, stockName,
			OrderSide.BUY, quantity, price, fee, BigDecimal.ZERO, BigDecimal.ZERO, balance);
	}

	public static OrderMatchedEvent ofSell(OrderType orderType, UUID orderNo, String stockCode,
										   String stockName, Integer quantity,
										   BigDecimal price, BigDecimal fee, BigDecimal tax,
										   BigDecimal realizedProfit, BigDecimal balance) {
		return new OrderMatchedEvent(orderType, orderNo, stockCode, stockName,
			OrderSide.SELL, quantity, price, fee, tax, realizedProfit, balance);
	}
}
