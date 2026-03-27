package com.solv.wefin.domain.trading.account.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "virtual_account")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class VirtualAccount {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long virtualAccountId;

	@Column(nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private BigDecimal balance;

	@Column(nullable = false)
	private BigDecimal initialBalance;

	private BigDecimal totalRealizedProfit = BigDecimal.ZERO;

	@LastModifiedDate
	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ")
	private LocalDateTime updatedAt;

	public VirtualAccount(UUID userId) {
		this.userId = userId;
		this.balance = new BigDecimal("10000000");
		this.initialBalance = new BigDecimal("10000000");
	}

	// === 비즈니스 메서드 ===
	public void deduct(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_AMOUNT);
		}
		if (this.balance.compareTo(amount) < 0) {
			throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_BALANCE);
		}
		this.balance = this.balance.subtract(amount);
	}

	public void deposit(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_AMOUNT);
		}
		this.balance = this.balance.add(amount);
	}

	public void addProfit(BigDecimal realizedProfit) {
		if (realizedProfit == null) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_AMOUNT);
		}
		this.totalRealizedProfit = this.totalRealizedProfit.add(realizedProfit);
	}
}
