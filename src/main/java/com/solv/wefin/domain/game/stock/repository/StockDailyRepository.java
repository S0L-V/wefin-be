package com.solv.wefin.domain.game.stock.repository;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StockDailyRepository extends JpaRepository<StockDaily, UUID> {

    List<StockDaily> findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
            StockInfo stockInfo, LocalDate startDate, LocalDate endDate);

    @Query("SELECT sd.tradeDate " +
            "FROM StockDaily sd " +
            "WHERE sd.stockInfo = :stockInfo " +
            "AND sd.tradeDate IN :dates ")
    Set<LocalDate> findExistingDates(@Param("stockInfo") StockInfo stockInfo,
                                     @Param("dates") List<LocalDate> dates);

    @Query("SELECT MAX(sd.tradeDate) FROM StockDaily sd WHERE sd.tradeDate <= :date")
    Optional<LocalDate> findLatestTradeDateOnOrBefore(@Param("date") LocalDate date);

    /** 수집된 주가 데이터의 최초 거래일 (방 생성 시 시작일 범위 하한용) */
    @Query("SELECT MIN(sd.tradeDate) FROM StockDaily sd")
    Optional<LocalDate> findEarliestTradeDate();

    /** 특정 종목의 특정 날짜 주가 데이터 조회 (매수/매도 시 시가 조회용) */
    Optional<StockDaily> findByStockInfoAndTradeDate(StockInfo stockInfo, LocalDate tradeDate);

    /** 여러 종목의 특정 날짜 주가 데이터 일괄 조회 (N+1 방지용) */
    List<StockDaily> findAllByStockInfoInAndTradeDate(List<StockInfo> stockInfos, LocalDate tradeDate);

   //키워드 검색. 짧은 키워드 대량 조회 제한
    @Query("SELECT sd FROM StockDaily sd " +
            "JOIN FETCH sd.stockInfo si " +
            "WHERE sd.tradeDate = :tradeDate " +
            "AND (si.stockName LIKE :keyword ESCAPE '\\' OR si.symbol LIKE :keyword ESCAPE '\\') " +
            "ORDER BY si.stockName ASC")
    List<StockDaily> searchByKeywordAndTradeDate(@Param("keyword") String keyword,
                                                 @Param("tradeDate") LocalDate tradeDate,
                                                 Pageable pageable);
}
