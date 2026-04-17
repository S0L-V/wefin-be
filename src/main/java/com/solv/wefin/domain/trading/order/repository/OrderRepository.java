package com.solv.wefin.domain.trading.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.solv.wefin.domain.trading.order.entity.OrderStatus;
import com.solv.wefin.domain.trading.order.entity.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.order.entity.Order;

import jakarta.persistence.LockModeType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

	Optional<Order> findByOrderNo(UUID orderNo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Order o WHERE o.orderNo = :orderNo")
	Optional<Order> findByOrderNoForUpdate(@Param("orderNo") UUID orderNo);

	List<Order> findAllByVirtualAccountIdOrderByCreatedAtDesc(Long virtualAccountId);

	/**
	 * 매칭 엔진 전용: PENDING/PARTIAL 상태의 지정가 주문을 비관적 쓰기 락으로 조회한다.
	 *
	 * <p>동시 WebSocket 틱에서 같은 주문을 이중 체결하는 TOCTOU 경쟁을 차단하기 위해 사용한다.
	 * 같은 종목에 대한 matchLimitOrders 호출이 여러 트랜잭션에서 동시에 일어나면 첫 번째가
	 * 락을 잡고 나머지는 대기하게 되어 체결 순서가 직렬화된다.
	 *
	 * <p>취소/수정 경로(findByOrderNoForUpdate)와도 같은 row에 대해 경쟁하므로
	 * cancel-vs-match race condition도 함께 해소된다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Order o " +
			"WHERE o.status IN :statuses " +
			"AND o.orderType = :orderType " +
			"AND o.stockId = :stockId")
	List<Order> findAllByStatusInAndOrderTypeAndStockIdForUpdate(
			@Param("statuses") List<OrderStatus> statuses,
			@Param("orderType") OrderType orderType,
			@Param("stockId") Long stockId
	);
}
