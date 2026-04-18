package com.solv.wefin.web.game.stock.dto.response;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class StockSearchResponse {

    private String symbol;
    private String stockName;
    private String market;
    private BigDecimal price;

    public static StockSearchResponse from(StockDaily stockDaily) {
        return new StockSearchResponse(
                stockDaily.getStockInfo().getSymbol(),
                stockDaily.getStockInfo().getStockName(),
                stockDaily.getStockInfo().getMarket(),
                stockDaily.getClosePrice()
        );
    }
}
