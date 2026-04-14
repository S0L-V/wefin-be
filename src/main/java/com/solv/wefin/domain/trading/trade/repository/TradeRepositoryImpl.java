package com.solv.wefin.domain.trading.trade.repository;

import static com.solv.wefin.domain.trading.trade.entity.QTrade.trade;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.trade.dto.TradeSearchCondition;
import com.solv.wefin.domain.trading.trade.entity.Trade;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TradeRepositoryImpl implements TradeRepositoryCustom {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Trade> searchTrades(Long virtualAccountId, TradeSearchCondition condition, Long cursor, int size) {
		return queryFactory
			.selectFrom(trade)
			.where(
				accountEq(virtualAccountId),
				stockIdEq(condition.stockId()),
				sideEq(condition.side()),
				createdAtGoe(condition.startDate()),
				createdAtLt(condition.endDate()),
				cursorLt(cursor)
			)
			.orderBy(trade.tradeId.desc())
			.limit(size + 1)
			.fetch();
	}

	private BooleanExpression accountEq(Long virtualAccountId) {
		return trade.virtualAccountId.eq(virtualAccountId);
	}

	private BooleanExpression stockIdEq(Long stockId) {
		return stockId != null ? trade.stockId.eq(stockId) : null;
	}

	private BooleanExpression createdAtGoe(LocalDate startDate) {
		if (startDate == null)
			return null;
		OffsetDateTime start = startDate.atStartOfDay(KST).toOffsetDateTime();
		return trade.createdAt.goe(start);
	}

	private BooleanExpression createdAtLt(LocalDate endDate) {
		if (endDate == null)
			return null;
		OffsetDateTime end = endDate.plusDays(1)
			.atStartOfDay(KST).toOffsetDateTime();
		return trade.createdAt.lt(end);
	}

	private BooleanExpression cursorLt(Long cursor) {
		return cursor != null ? trade.tradeId.lt(cursor) : null;
	}

	private BooleanExpression sideEq(OrderSide side) {
		return side != null ? trade.side.eq(side) : null;
	}
}
