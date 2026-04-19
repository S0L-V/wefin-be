package com.solv.wefin.domain.game.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock_info")
public class StockInfo {

    @Id
    @Column(name = "symbol")
    private String symbol;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "market", nullable = false)
    private String market;

    @Column(name = "sector")
    private String sector;

    @Column(name = "keyword", length = 100)
    private String keyword;

    public static StockInfo create(String symbol, String stockName, String market, String sector) {
        StockInfo stockInfo = new StockInfo();
        stockInfo.symbol = symbol;
        stockInfo.stockName = stockName;
        stockInfo.market = market;
        stockInfo.sector = sector;
        return stockInfo;
    }

    public static StockInfo create(String symbol, String stockName, String market, String sector, String keyword) {
        StockInfo stockInfo = create(symbol, stockName, market, sector);
        stockInfo.keyword = keyword;
        return stockInfo;
    }
}
