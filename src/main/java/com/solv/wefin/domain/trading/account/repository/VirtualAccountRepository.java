package com.solv.wefin.domain.trading.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;

import jakarta.persistence.LockModeType;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

	Optional<VirtualAccount> findByUserId(UUID userId);

	/**
	 * 예수금 변경 시 비관적 락으로 조회
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM VirtualAccount a WHERE a.virtualAccountId = :id")
	Optional<VirtualAccount> findByIdForUpdate(@Param("id") Long virtualAccountId);

}
