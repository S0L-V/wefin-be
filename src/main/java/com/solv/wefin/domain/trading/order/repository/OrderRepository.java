package com.solv.wefin.domain.trading.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.order.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByOrderNo(UUID orderNo);

	List<Order> findAllByVirtualAccountIdOrderByCreatedAtDesc(Long virtualAccountId);
}
