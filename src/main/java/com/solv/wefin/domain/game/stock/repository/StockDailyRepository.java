package com.solv.wefin.domain.game.stock.repository;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StockDailyRepository extends JpaRepository<StockDaily, UUID> {

    List<StockDaily> findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
            StockInfo stockInfo, LocalDate startDate, LocalDate endDate);

    @Query("SELECT sd.tradeDate FROM StockDaily sd WHERE sd.stockInfo = :stockInfo AND sd.tradeDate IN :dates")
    Set<LocalDate> findExistingDates(@Param("stockInfo") StockInfo stockInfo,
                                     @Param("dates") List<LocalDate> dates);
}
