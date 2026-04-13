package com.solv.wefin.web.trading.watchlist.dto;

import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.stock.entity.Stock;

import java.math.BigDecimal;

public record WatchlistResponse(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        float changeRate
) {
    public static WatchlistResponse from(Stock stock, PriceResponse price) {
        return new WatchlistResponse(
                stock.getStockCode(),
                stock.getStockName(),
                price.currentPrice(),
                price.changeRate()
        );
    }
}
