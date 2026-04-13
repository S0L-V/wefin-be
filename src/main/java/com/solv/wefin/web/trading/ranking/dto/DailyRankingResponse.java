package com.solv.wefin.web.trading.ranking.dto;

import java.math.BigDecimal;
import java.util.List;

import com.solv.wefin.domain.trading.ranking.dto.DailyRankingInfo;

public record DailyRankingResponse (
	List<RankingItem> rankings,
	MyRank myRank
) {
	public record RankingItem(
		int rank,
		String nickname,
		BigDecimal realizedProfit,
		int tradeCount
	) {}

	public record MyRank(
		int rank,
		BigDecimal realizedProfit
	) {}

	public static DailyRankingResponse from(DailyRankingInfo info) {
		List<RankingItem> rankings = info.rankings().stream()
			.map(item -> new RankingItem(
				item.rank(),
				item.nickname(),
				item.realizedProfit(),
				item.tradeCount()
			)).toList();

		MyRank myRank = (info.myRank() != null)
			? new MyRank(info.myRank().rank(), info.myRank().realizedProfit())
			: null;

		return new DailyRankingResponse(rankings, myRank);
	}
}
