package com.solv.wefin.domain.trading.stock.repository;

import com.solv.wefin.domain.trading.stock.entity.Stock;

import java.util.List;

public interface StockRepositoryCustom {
    List<Stock> search(String keyword, String market);
}
