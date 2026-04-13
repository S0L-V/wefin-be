package com.solv.wefin.domain.trading.watchlist.dto;

import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.stock.entity.Stock;

public record WatchlistInfo(Stock stock, PriceResponse price) {
}
