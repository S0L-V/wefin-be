package com.solv.wefin.domain.game.participant.dto;

import java.math.BigDecimal;

public record PortfolioInfo(
        BigDecimal seedMoney,
        BigDecimal cash,
        BigDecimal stockValue,
        BigDecimal totalAsset,
        BigDecimal profitRate
) {
}
