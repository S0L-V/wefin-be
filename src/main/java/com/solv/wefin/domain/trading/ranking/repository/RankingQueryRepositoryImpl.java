package com.solv.wefin.domain.trading.ranking.repository;

import static com.solv.wefin.domain.trading.trade.entity.QTrade.trade;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.ranking.dto.DailyRankingRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RankingQueryRepositoryImpl implements RankingQueryRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<DailyRankingRow> findDailySellAggregates(OffsetDateTime startInclusive,
														 OffsetDateTime endExclusive) {
		return queryFactory
			.select(Projections.constructor(
				DailyRankingRow.class,
				trade.virtualAccountId,
				trade.realizedProfit.sum(),
				trade.tradeId.count()
			))
			.from(trade)
			.where(
				trade.side.eq(OrderSide.SELL),
				trade.createdAt.goe(startInclusive),
				trade.createdAt.lt(endExclusive),
				trade.realizedProfit.isNotNull()
			)
			.groupBy(trade.virtualAccountId)
			.orderBy(trade.realizedProfit.sum().desc())
			.fetch();
	}

}
