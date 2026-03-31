package com.solv.wefin.domain.trading.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.trade.repository.TradeRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;
	@Mock
	private TradeRepository tradeRepository;
	@Mock
	private MarketPriceProvider marketPriceProvider;
	@Mock
	private VirtualAccountService virtualAccountService;
	@Mock
	private PortfolioService portfolioService;

	@InjectMocks
	private OrderService orderService;

	@Test
	void 매수_성공() {
		// given
		when(marketPriceProvider.getCurrentPrice("005930"))
			.thenReturn(new BigDecimal("170000"));
		when(virtualAccountService.deductBalance(eq(1L), any()))
			.thenReturn(new VirtualAccount(UUID.randomUUID()));
		when(orderRepository.save(any()))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeRepository.save(any()))
			.thenAnswer(invocation -> invocation.getArgument(0));

		// when
		orderService.buyMarket(1L, 1L, "005930", 20);

		// then
		verify(orderRepository).save(any());
		verify(tradeRepository).save(any());
		verify(portfolioService).addHolding(eq(1L), eq(1L), eq(20), any(), any());
	}

	@Test
	void 수량_0_이하() {
		// when & then
		assertThatThrownBy(() -> orderService.buyMarket(1L, 1L, "005930", 0))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void 예수금_부족() {
		// given
		when(marketPriceProvider.getCurrentPrice("005930"))
			.thenReturn(new BigDecimal("178000"));
		when(virtualAccountService.deductBalance(any(), any()))
			.thenThrow(new BusinessException(ErrorCode.ORDER_INSUFFICIENT_BALANCE));

		// when & then
		assertThatThrownBy(() -> orderService.buyMarket(1L, 1L, "005930", 20))
			.isInstanceOf(BusinessException.class);
	}
}