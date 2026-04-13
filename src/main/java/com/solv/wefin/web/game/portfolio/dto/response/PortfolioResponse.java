package com.solv.wefin.web.game.portfolio.dto.response;

import com.solv.wefin.domain.game.participant.dto.PortfolioInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PortfolioResponse {

    private BigDecimal seedMoney;
    private BigDecimal cash;
    private BigDecimal stockValue;
    private BigDecimal totalAsset;
    private BigDecimal profitRate;

    public static PortfolioResponse from(PortfolioInfo info) {
        return new PortfolioResponse(
                info.seedMoney(),
                info.cash(),
                info.stockValue(),
                info.totalAsset(),
                info.profitRate()
        );
    }
}
