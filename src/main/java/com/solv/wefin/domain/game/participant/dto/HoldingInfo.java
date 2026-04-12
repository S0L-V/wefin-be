package com.solv.wefin.domain.game.participant.dto;

import java.math.BigDecimal;

public record HoldingInfo(
        String symbol,
        String stockName,
        int quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal evalAmount,
        BigDecimal profitRate
) {
}
