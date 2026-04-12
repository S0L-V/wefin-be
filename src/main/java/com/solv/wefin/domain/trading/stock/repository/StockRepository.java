package com.solv.wefin.domain.trading.stock.repository;

import com.solv.wefin.domain.trading.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long>, StockRepositoryCustom {
    boolean existsByStockCode(String stockCode);
    Optional<Stock> findByStockCode(String stockCode);
    Optional<Stock> findByStockName(String stockName);
}
