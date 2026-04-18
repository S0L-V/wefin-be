package com.solv.wefin.domain.trading.ranking.dto;

import java.math.BigDecimal;
import java.util.List;

public record DailyRankingInfo(
	List<RankingItemInfo> rankings,
	MyRankInfo myRank
) {
	public record RankingItemInfo(
		int rank,
		String nickname,
		BigDecimal realizedProfit,
		int tradeCount
	) {}

	public record MyRankInfo(
		int rank,
		BigDecimal realizedProfit
	) {}
}
