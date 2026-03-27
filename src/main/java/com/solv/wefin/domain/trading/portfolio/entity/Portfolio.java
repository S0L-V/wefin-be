package com.solv.wefin.domain.trading.portfolio.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.solv.wefin.global.common.BaseEntity;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "portfolio",
	uniqueConstraints = @UniqueConstraint(columnNames = {"virtual_account_id", "stock_id"})
)
@Getter
@NoArgsConstructor
public class Portfolio extends BaseEntity {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long portfolioId;

	@Column(nullable = false)
	private Long virtualAccountId;

	@Column(nullable = false)
	private Long stockId;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false)
	private BigDecimal avgPrice;

	@Column(nullable = false)
	private BigDecimal totalBuyAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	public Portfolio(Long virtualAccountId, Long stockId, Integer quantity, BigDecimal price, Currency currency) {
		this.virtualAccountId = virtualAccountId;
		this.stockId = stockId;
		this.quantity = quantity;
		this.avgPrice = price;
		this.currency = currency;
		this.totalBuyAmount = price.multiply(BigDecimal.valueOf(quantity));
	}

	// === 비즈니스 메서드 ===
	public void addQuantity(Integer quantity, BigDecimal price) {
		this.totalBuyAmount = this.totalBuyAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
		this.quantity += quantity;
		this.avgPrice = this.totalBuyAmount.divide(BigDecimal.valueOf(this.quantity), 2, RoundingMode.HALF_UP);
	}

	public void deductQuantity(Integer quantity) {
		if (this.quantity.compareTo(quantity) < 0) {
			throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_HOLDINGS);
		}
		this.quantity -= quantity;
		this.totalBuyAmount = this.avgPrice.multiply(BigDecimal.valueOf(this.quantity));
	}
}
