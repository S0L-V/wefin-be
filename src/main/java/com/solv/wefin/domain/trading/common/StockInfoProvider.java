package com.solv.wefin.domain.trading.common;

import com.solv.wefin.domain.trading.stock.entity.Stock;

public interface StockInfoProvider {
    boolean existsByCode(String stockCode);
    Stock getStock(Long stockId);
    String getStockName(String stockCode);
    String getMarket(String stockCode);
}
