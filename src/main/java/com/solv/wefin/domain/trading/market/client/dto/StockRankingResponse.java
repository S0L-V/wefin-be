package com.solv.wefin.domain.trading.market.client.dto;

import java.util.List;

public record StockRankingResponse(List<StockRankingItem> items) {
	public static StockRankingResponse from(List<StockRankingItem> items) {
		return new StockRankingResponse(items);
	}
}
