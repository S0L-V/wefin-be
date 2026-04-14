package com.solv.wefin.web.trading.trade.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.trade.entity.Trade;

public record TradeHistoryResponse(
	Long tradeId,
	UUID tradeNo,
	String stockCode,
	String stockName,
	String side,
	Integer quantity,
	BigDecimal price,
	BigDecimal totalAmount,
	BigDecimal fee,
	BigDecimal tax,
	BigDecimal realizedProfit,
	OffsetDateTime createdAt
) {
	public static TradeHistoryResponse from(Trade trade, String stockCode, String stockName) {
		return new TradeHistoryResponse(
			trade.getTradeId(),
			trade.getTradeNo(),
			stockCode,
			stockName,
			trade.getSide().name(),
			trade.getQuantity(),
			trade.getPrice(),
			trade.getTotalAmount(),
			trade.getFee(),
			trade.getTax(),
			trade.getRealizedProfit(),
			trade.getCreatedAt()
		);
	}
}
