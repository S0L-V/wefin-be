package com.solv.wefin.web.game.portfolio.dto.response;

import com.solv.wefin.domain.game.participant.dto.HoldingInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class HoldingResponse {

    private String symbol;
    private String stockName;
    private int quantity;
    private BigDecimal avgPrice;
    private BigDecimal currentPrice;
    private BigDecimal evalAmount;
    private BigDecimal profitRate;

    public static HoldingResponse from(HoldingInfo info) {
        return new HoldingResponse(
                info.symbol(),
                info.stockName(),
                info.quantity(),
                info.avgPrice(),
                info.currentPrice(),
                info.evalAmount(),
                info.profitRate()
        );
    }
}
