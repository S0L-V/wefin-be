package com.solv.wefin.domain.trading.order.repository;

import static com.solv.wefin.domain.trading.order.entity.QOrder.order;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.solv.wefin.domain.trading.order.dto.OrderSearchCondition;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderStatus;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Order> searchOrders(Long virtualAccountId, OrderSearchCondition condition,
									Long cursor, int size) {
		return queryFactory
			.selectFrom(order)
			.where(
				accountEq(virtualAccountId),
				statusIn(condition.statuses()),
				stockIdEq(condition.stockId()),
				createdAtGoe(condition.startDate()),
				createdAtLt(condition.endDate()),
				cursorLt(cursor)
			)
			.orderBy(order.orderId.desc())
			.limit(size + 1)
			.fetch();
	}

	@Override
	public List<Order> findPendingOrders(Long virtualAccountId) {
		return queryFactory
			.selectFrom(order)
			.where(
				accountEq(virtualAccountId),
				order.status.eq(OrderStatus.PENDING)
			)
			.orderBy(order.orderId.desc())
			.fetch();
	}

	/*
	 * 오늘 최종 체결된 주문을 조회한다.
	 *
	 * FILLED 상태의 주문은 validatePending()이 모든 변경(fill, fillPartially,
	 * cancel, modify)을 차단하므로, 최종 체결 이후에는 엔티티 mutation이 발생하지 않는다.
	 * 따라서 FILLED 주문에 한해 updatedAt이 사실상 체결 시각과 동일하다.
	 *
	 * 주의: "FILLED 주문 불변" 가정이 깨지면 이 쿼리도 깨진다. 향후 FILLED 주문을
	 * 변경하는 로직이 도입되면 별도 filledAt 컬럼 도입을 검토해야 한다.
	 */
	@Override
	public List<Order> findTodayFilledOrders(Long virtualAccountId, LocalDate today) {
		OffsetDateTime startOfDay = today.atStartOfDay(KST).toOffsetDateTime();
		OffsetDateTime startOfNextDay = today.plusDays(1)
			.atStartOfDay(KST).toOffsetDateTime();

		return queryFactory
			.selectFrom(order)
			.where(
					accountEq(virtualAccountId),
					order.status.eq(OrderStatus.FILLED),
					order.updatedAt.goe(startOfDay),
					order.updatedAt.lt(startOfNextDay)
			)
			.orderBy(order.orderId.desc())
			.fetch();
	}

	private BooleanExpression accountEq(Long virtualAccountId) {
		return order.virtualAccountId.eq(virtualAccountId);
	}

	private BooleanExpression statusIn(List<OrderStatus> statuses) {
		return (statuses != null && !statuses.isEmpty()) ? order.status.in(statuses) : null;
	}

	private BooleanExpression stockIdEq(Long stockId) {
		return stockId != null ? order.stockId.eq(stockId) : null;
	}

	private BooleanExpression createdAtGoe(LocalDate startDate) {
		if (startDate == null)
			return null;
		OffsetDateTime start = startDate.atStartOfDay(KST).toOffsetDateTime();
		return order.createdAt.goe(start);
	}

	private BooleanExpression createdAtLt(LocalDate endDate) {
		if (endDate == null)
			return null;
		OffsetDateTime end = endDate.plusDays(1)
			.atStartOfDay(KST).toOffsetDateTime();
		return order.createdAt.lt(end);
	}

	private BooleanExpression cursorLt(Long cursor) {
		return cursor != null ? order.orderId.lt(cursor) : null;
	}
}
