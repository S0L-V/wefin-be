package com.solv.wefin.domain.trading.order.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.common.TradingConstants;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;
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

	private BigDecimal reservedAvgPrice;

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

	public Order(Long virtualAccountId, Long stockId, OrderType orderType, OrderSide side, Integer quantity,
				 BigDecimal requestPrice, Currency currency,
				 BigDecimal exchangeRate, BigDecimal fee, BigDecimal tax,
				 BigDecimal reservedAvgPrice) {
		this(virtualAccountId, stockId, orderType, side, quantity, requestPrice, currency,
				exchangeRate, fee, tax);
		this.reservedAvgPrice = reservedAvgPrice;
	}

	// == 비즈니스 메서드 ==
	public void fill(Integer filledQuantity) {
		validatePending();
		if (filledQuantity == null || filledQuantity <= 0 || filledQuantity > this.quantity) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}
		this.status = OrderStatus.FILLED;
		this.filledQuantity = filledQuantity;
	}

	public void fillPartially(Integer matchedQuantity) {
		validatePending();

		if (matchedQuantity == null || matchedQuantity > this.quantity - this.filledQuantity || matchedQuantity <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}
		this.filledQuantity = this.filledQuantity + matchedQuantity;

		if (this.filledQuantity.equals(this.quantity)) {
			this.status = OrderStatus.FILLED;
		} else {
			this.status = OrderStatus.PARTIAL;
		}
	}

	public void cancel() {
		validatePending();
		this.status = OrderStatus.CANCELLED;
		this.cancelledAt = OffsetDateTime.now();
	}

	public void modify(BigDecimal newPrice, Integer newQuantity) {
		validatePending();
		if (newQuantity == null || newQuantity <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}
		if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_AMOUNT);
		}
		this.requestPrice = newPrice;
		this.quantity = newQuantity;

		this.fee = newPrice.multiply(BigDecimal.valueOf(newQuantity))
			.multiply(TradingConstants.FEE_RATE).setScale(0, RoundingMode.DOWN);
		if (this.side == OrderSide.SELL) {
			this.tax = newPrice.multiply(BigDecimal.valueOf(newQuantity))
				.multiply(TradingConstants.TAX_RATE).setScale(0, RoundingMode.DOWN);
		}
	}

	public void validateOwnership(Long virtualAccountId) {
		if (!this.virtualAccountId.equals(virtualAccountId)) {
			throw new BusinessException(ErrorCode.ORDER_OWNERSHIP_MISMATCH);
		}
	}

	private void validatePending() {
		if (this.status == OrderStatus.FILLED) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_FILLED);
		}
		if (this.status == OrderStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
		}
	}
}
