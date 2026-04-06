package com.solv.wefin.domain.game.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock_daily", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"symbol", "trade_date"})
})
public class StockDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "daily_id")
    private UUID dailyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false)
    private StockInfo stockInfo;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private BigDecimal volume;

    @Column(name = "change_rate")
    private BigDecimal changeRate;

    public static StockDaily create(StockInfo stockInfo, LocalDate tradeDate,
                                     BigDecimal openPrice, BigDecimal highPrice,
                                     BigDecimal lowPrice, BigDecimal closePrice,
                                     BigDecimal volume, BigDecimal changeRate) {
        StockDaily stockDaily = new StockDaily();
        stockDaily.stockInfo = stockInfo;
        stockDaily.tradeDate = tradeDate;
        stockDaily.openPrice = openPrice;
        stockDaily.highPrice = highPrice;
        stockDaily.lowPrice = lowPrice;
        stockDaily.closePrice = closePrice;
        stockDaily.volume = volume;
        stockDaily.changeRate = changeRate;
        return stockDaily;
    }
}
