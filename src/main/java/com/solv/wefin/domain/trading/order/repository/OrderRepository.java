package com.solv.wefin.domain.trading.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
