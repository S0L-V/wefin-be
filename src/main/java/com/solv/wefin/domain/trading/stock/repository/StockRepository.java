package com.solv.wefin.domain.trading.stock.repository;

import com.solv.wefin.domain.trading.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long>, StockRepositoryCustom {
    boolean existsByStockCode(String stockCode);
    Optional<Stock> findByStockCode(String stockCode);

    /**
     * 모든 종목의 코드와 정식 이름을 반환한다.
     * AI 태깅 검증 시 코드 비교 및 종목명 정규화(canonicalize)에 사용된다.
     */
    @Query("SELECT s.stockCode AS code, s.stockName AS name FROM Stock s")
    List<StockCodeNameProjection> findAllStockCodeNamePairs();

    interface StockCodeNameProjection {
        String getCode();
        String getName();
    }
}
