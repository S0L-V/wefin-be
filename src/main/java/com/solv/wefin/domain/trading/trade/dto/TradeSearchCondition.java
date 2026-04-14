package com.solv.wefin.domain.trading.trade.dto;

import java.time.LocalDate;

import com.solv.wefin.domain.trading.order.entity.OrderSide;

public record TradeSearchCondition(
	Long stockId,
	OrderSide side,
	LocalDate startDate,
	LocalDate endDate
) {
}
