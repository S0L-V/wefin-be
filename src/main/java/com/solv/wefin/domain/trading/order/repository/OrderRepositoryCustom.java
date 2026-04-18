package com.solv.wefin.domain.trading.order.repository;

import java.time.LocalDate;
import java.util.List;

import com.solv.wefin.domain.trading.order.dto.OrderSearchCondition;
import com.solv.wefin.domain.trading.order.entity.Order;

public interface OrderRepositoryCustom {

	/**
	 * 주문 내역 검색 (커서 기반 페이지네이션 + 동적 필터)
	 */
	List<Order> searchOrders(Long virtualAccountId, OrderSearchCondition condition,
							 Long cursor, int size);

	/**
	 * 미체결 주문 조회 (PENDING만)
	 */
	List<Order> findPendingOrders(Long virtualAccountId);

	/**
	 * 오늘 체결 주문 조회 (FILLED + 당일)
	 */
	List<Order> findTodayFilledOrders(Long virtualAccountId, LocalDate today);
}
