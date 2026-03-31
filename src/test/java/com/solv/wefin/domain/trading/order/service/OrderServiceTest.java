package com.solv.wefin.domain.trading.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

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
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.trade.service.TradeService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;
	@Mock
	private TradeService tradeService;
	@Mock
	private VirtualAccountService virtualAccountService;
	@Mock
	private PortfolioService portfolioService;
	@Mock
	private MarketPriceProvider marketPriceProvider;
	@Mock
	private StockInfoProvider stockInfoProvider;

	@InjectMocks
	private OrderService orderService;

	@Test
	void 매수_성공() {
		// given
		Stock mockStock = mock(Stock.class);

		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(mockStock.getStockCode()).willReturn("005930");
		given(marketPriceProvider.getCurrentPrice("005930"))
			.willReturn(new BigDecimal("170000"));
		given(virtualAccountService.deductBalance(eq(1L), any()))
			.willReturn(new VirtualAccount(UUID.randomUUID()));
		given(orderRepository.save(any()))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		orderService.buyMarket(1L, 1L, 20);

		// then
		verify(orderRepository).save(any());
		verify(tradeService).createBuyTrade(any(), eq(1L), eq(1L), eq(20), any(), any(), any(), any(), any());
		verify(portfolioService).addHolding(eq(1L), eq(1L), eq(20), any(), any());
	}

	@Test
	void 수량_0_이하() {
		// when & then
		assertThatThrownBy(() -> orderService.buyMarket(1L, 1L, 0))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void 예수금_부족() {
		// given
		Stock mockStock = mock(Stock.class);
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(mockStock.getStockCode()).willReturn("005930");
		given(marketPriceProvider.getCurrentPrice("005930"))
			.willReturn(new BigDecimal("178000"));
		given(virtualAccountService.deductBalance(any(), any()))
			.willThrow(new BusinessException(ErrorCode.ORDER_INSUFFICIENT_BALANCE));

		// when & then
		assertThatThrownBy(() -> orderService.buyMarket(1L, 1L, 20))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void 매도_성공() {
		// given
		Stock mockStock = mock(Stock.class);
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(mockStock.getStockCode()).willReturn("005930");
		given(marketPriceProvider.getCurrentPrice("005930"))
			.willReturn(new BigDecimal("178000"));

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getAvgPrice()).willReturn(new BigDecimal("170000"));
		given(mockPortfolio.getQuantity()).willReturn(20);
		given(portfolioService.getPortfolioForUpdate(1L, 1L))
			.willReturn(mockPortfolio);

		given(orderRepository.save(any()))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		orderService.sellMarket(1L, 1L, 10);

		// then
		verify(tradeService).createSellTrade(any(), eq(1L), eq(1L), eq(10),
			any(), any(), any(), any(), any(), any(), any());
		verify(portfolioService).deductQuantity(eq(1L), eq(1L), eq(10));
		verify(virtualAccountService).depositBalance(eq(1L), any());
		verify(virtualAccountService).addRealizedProfit(eq(1L), any());
	}

	@Test
	void 매도_미보유_종목() {
		// given
		Stock mockStock = mock(Stock.class);
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(mockStock.getStockCode()).willReturn("005930");
		given(marketPriceProvider.getCurrentPrice("005930"))
			.willReturn(new BigDecimal("178000"));
		given(portfolioService.getPortfolioForUpdate(1L, 1L))
			.willThrow(new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD));

		// when & then
		assertThatThrownBy(() -> orderService.sellMarket(1L, 1L, 10))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void 매도_보유수량_부족() {
		// given
		Stock mockStock = mock(Stock.class);
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(mockStock.getStockCode()).willReturn("005630");
		given(marketPriceProvider.getCurrentPrice("005630"))
			.willReturn(new BigDecimal("178000"));

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getQuantity()).willReturn(20);
		given(portfolioService.getPortfolioForUpdate(1L, 1L))
			.willReturn(mockPortfolio);

		// when & then
		assertThatThrownBy(() -> orderService.sellMarket(1L, 1L, 30))
			.isInstanceOf(BusinessException.class);
	}
}