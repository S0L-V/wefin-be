package com.solv.wefin.domain.trading.trade.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.trade.entity.Trade;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

	Optional<Trade> findByTradeNo(UUID tradeNo);

	List<Trade> findAllByVirtualAccountIdOrderByCreatedAtDesc(Long virtualAccountId);

	List<Trade> findAllByOrderIdOrderByCreatedAtDesc(Long orderId);
}
