package com.solv.wefin.domain.trading.stock.dto;

import com.solv.wefin.domain.trading.stock.entity.Stock;

public record StockSearchResponse(
        String stockCode,
        String stockName,
        String market,
        String sector
) {
    public static StockSearchResponse from(Stock stock) {
        return new StockSearchResponse(
                stock.getStockCode(),
                stock.getStockName(),
                stock.getMarket(),
                stock.getSector()
        );
    }
}
