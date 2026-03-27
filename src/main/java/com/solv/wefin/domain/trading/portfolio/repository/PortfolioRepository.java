package com.solv.wefin.domain.trading.portfolio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;

import jakarta.persistence.LockModeType;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

	Optional<Portfolio> findByVirtualAccountIdAndStockId(Long virtualAccountId, Long stockId);

	List<Portfolio> findByVirtualAccountId(Long virtualAccountId);

	/**
	 * 매수/매도 시 비관적 락으로 포트폴리오 조회
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Portfolio p WHERE p.virtualAccountId = :virtualAccountId AND p.stockId = :stockId")
	Optional<Portfolio> findByVirtualAccountIdAndStockIdForUpdate(
		@Param("virtualAccountId") Long virtualAccountId,
		@Param("stockId") Long stockId
	);
}
