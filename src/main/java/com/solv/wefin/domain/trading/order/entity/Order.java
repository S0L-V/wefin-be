package com.solv.wefin.domain.trading.order.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long orderId;

	@Column(unique = true, nullable = false)
	private UUID orderNo;

	@Column(nullable = false)
	private Long virtualAccountId;

	@Column(nullable = false)
	private Long stockId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderType orderType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderSide side;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false)
	private Integer filledQuantity = 0;

	private BigDecimal requestPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status = OrderStatus.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	private BigDecimal exchangeRate;

	@Column(nullable = false)
	private BigDecimal fee;

	@Column(nullable = false)
	private BigDecimal tax;

	private OffsetDateTime cancelledAt;

	public Order(Long virtualAccountId, Long stockId, OrderType orderType, OrderSide side, Integer quantity,
				 BigDecimal requestPrice, Currency currency,
				 BigDecimal exchangeRate, BigDecimal fee, BigDecimal tax) {
		this.orderNo = UUID.randomUUID();
		this.virtualAccountId = virtualAccountId;
		this.stockId = stockId;
		this.orderType = orderType;
		this.side = side;
		this.quantity = quantity;
		this.requestPrice = requestPrice;
		this.currency = currency;
		this.exchangeRate = exchangeRate;
		this.fee = fee;
		this.tax = tax;
	}
}
