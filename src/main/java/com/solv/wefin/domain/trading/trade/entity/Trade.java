package com.solv.wefin.domain.trading.trade.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long tradeId;

	@Column(unique = true, nullable = false, updatable = false)
	private UUID tradeNo;

	@Column(nullable = false)
	private Long orderId;

	@Column(nullable = false)
	private Long virtualAccountId;

	@Column(nullable = false)
	private Long stockId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderSide side;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false)
	private BigDecimal price;

	@Column(nullable = false)
	private BigDecimal totalAmount;

	@Column(nullable = false)
	private BigDecimal fee;

	@Column(nullable = false)
	private BigDecimal tax;

	private BigDecimal realizedProfit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	private BigDecimal exchangeRate;

	@CreatedDate
	@Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
	private OffsetDateTime createdAt;

	public Trade(Long orderId, Long virtualAccountId, Long stockId, OrderSide side, Integer quantity, BigDecimal price,
				 BigDecimal totalAmount, BigDecimal fee, BigDecimal tax, BigDecimal realizedProfit, Currency currency,
				 BigDecimal exchangeRate) {
		this.tradeNo = UUID.randomUUID();
		this.orderId = orderId;
		this.virtualAccountId = virtualAccountId;
		this.stockId = stockId;
		this.side = side;
		this.quantity = quantity;
		this.price = price;
		this.totalAmount = totalAmount;
		this.fee = fee;
		this.tax = tax;
		this.realizedProfit = realizedProfit;
		this.currency = currency;
		this.exchangeRate = exchangeRate;
	}

	public static Trade createBuyTrade(Long orderId, Long virtualAccountId, Long stockId,
									   Integer quantity, BigDecimal price, BigDecimal totalAmount, BigDecimal fee,
									   Currency currency, BigDecimal exchangeRate) {
		return new Trade(orderId, virtualAccountId, stockId, OrderSide.BUY, quantity, price, totalAmount, fee,
			BigDecimal.ZERO, null, currency, exchangeRate);
	}
}
