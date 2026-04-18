package com.solv.wefin.domain.trading.order.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.solv.wefin.domain.trading.common.TradingConstants;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

class OrderTest {

	private Order createBuyOrder() {
		return new Order(1L, 1L, OrderType.LIMIT, OrderSide.BUY, 10,
				BigDecimal.valueOf(50000), Currency.KRW, null,
				BigDecimal.valueOf(75), BigDecimal.ZERO);
	}

	private Order createSellOrder() {
		return new Order(1L, 1L, OrderType.LIMIT, OrderSide.SELL, 5,
				BigDecimal.valueOf(120000), Currency.KRW, null,
				BigDecimal.valueOf(75), BigDecimal.ZERO);
	}

	@Nested
	class CancelTest {
		@Test
		void 취소_성공() {
			Order order = createBuyOrder();
			order.cancel();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(order.getCancelledAt()).isNotNull();
		}

		@Test
		void 취소_실패_이미체결() {
			Order order = createBuyOrder();
			order.fill(10);
			assertThatThrownBy(() -> order.cancel())
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_ALREADY_FILLED.getMessage());
		}

		@Test
		void 취소_실패_이미취소() {
			Order order = createBuyOrder();
			order.cancel();
			assertThatThrownBy(() -> order.cancel())
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_ALREADY_CANCELLED.getMessage());
		}
	}

	@Nested
	class ModifyTest {
		@Test
		void 정정_성공_가격수량변경() {
			Order order = createBuyOrder();
			order.modify(new BigDecimal("12000"), 20);

			assertThat(order.getQuantity()).isEqualTo(20);
			assertThat(order.getRequestPrice())
					.isEqualByComparingTo(new BigDecimal("12000"));
			BigDecimal newFee = BigDecimal.valueOf(12000)
					.multiply(BigDecimal.valueOf(20))
					.multiply(TradingConstants.FEE_RATE);
			assertThat(order.getFee()).isEqualByComparingTo(newFee);
		}

		@Test
		void 정정_성공_매도주문_세금재계산() {
			Order order = createSellOrder();
			order.modify(new BigDecimal("15000"), 10);
			BigDecimal newTax = BigDecimal.valueOf(15000)
					.multiply(BigDecimal.valueOf(10))
					.multiply(TradingConstants.TAX_RATE);
			assertThat(order.getTax()).isEqualByComparingTo(newTax);
		}

		@Test
		void 정정_실패_이미체결() {
			Order order = createBuyOrder();
			order.fill(10);
			assertThatThrownBy(() -> order.modify(new BigDecimal(120000), 20))
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_ALREADY_FILLED.getMessage());
		}

		@Test
		void 정정_실패_수량_0이하() {
			Order order = createBuyOrder();
			assertThatThrownBy(() -> order.modify(new BigDecimal(120000), 0))
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_INVALID_QUANTITY.getMessage());
		}

		@Test
		void 정정_실패_가격_null() {
			Order order = createBuyOrder();
			assertThatThrownBy(() -> order.modify(null, 10))
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_INVALID_AMOUNT.getMessage());
		}
	}

	@Nested
	class OwnershipValidationTest {
		@Test
		void 소유권검증_성공() {
			Order order = createBuyOrder();
			assertThatCode(() -> order.validateOwnership(1L))
					.doesNotThrowAnyException();
		}

		@Test
		void 소유권검증_실패_불일치() {
			Order order = createBuyOrder();
			assertThatThrownBy(() -> order.validateOwnership(999L))
					.isInstanceOf(BusinessException.class)
					.hasMessage(ErrorCode.ORDER_OWNERSHIP_MISMATCH.getMessage());
		}
	}
}