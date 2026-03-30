package com.solv.wefin.domain.trading.stock.dto;

public record StockSearchResponse(
        String stockCode,
        String stockName,
        String market,
        String sector
) {
}
