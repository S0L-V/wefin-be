package com.solv.wefin.web.game.stock.dto.response;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ChartResponse {

    private LocalDate tradeDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private BigDecimal changeRate;

    public static ChartResponse from(StockDaily stockDaily) {
        return new ChartResponse(
                stockDaily.getTradeDate(),
                stockDaily.getOpenPrice(),
                stockDaily.getHighPrice(),
                stockDaily.getLowPrice(),
                stockDaily.getClosePrice(),
                stockDaily.getVolume(),
                stockDaily.getChangeRate()
        );
    }
}
