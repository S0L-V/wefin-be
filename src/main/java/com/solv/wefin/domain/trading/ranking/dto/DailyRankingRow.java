package com.solv.wefin.domain.trading.ranking.dto;

import java.math.BigDecimal;

public record DailyRankingRow(
	Long virtualAccountId,
	BigDecimal realizedProfitSum,
	Long tradeCount
) {
}
