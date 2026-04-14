package com.solv.wefin.web.trading.trade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.domain.trading.trade.entity.Trade;
import com.solv.wefin.domain.trading.trade.service.TradeService;
import com.solv.wefin.global.config.security.JwtProvider;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TradeService tradeService;
	@MockitoBean
	private VirtualAccountService accountService;
	@MockitoBean
	private StockService stockService;
	@MockitoBean
	private JwtProvider jwtProvider;

	private VirtualAccount mockAccount;
	private UUID testUserId;

	@BeforeEach
	void setUp() {
		testUserId = UUID.randomUUID();
		mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getVirtualAccountId()).willReturn(1L);
		given(accountService.getAccountByUserId(any())).willReturn(mockAccount);
	}

	@Test
	void 체결내역_조회_성공() throws Exception {
		// given
		Trade trade = mockTrade(1L, 100L);
		Stock stock = mockStock(100L, "005930", "삼성전자");

		given(tradeService.searchTrades(anyLong(), any(), any(), anyInt()))
			.willReturn(List.of(trade));
		given(stockService.findAllByIdIn(anyList()))
			.willReturn(List.of(stock));

		// when & then
		mockMvc.perform(get("/api/trade/history")
				.param("size", "20")
				.with(authentication(
					new UsernamePasswordAuthenticationToken(testUserId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content").isArray())
			.andExpect(jsonPath("$.data.content[0].stockCode").value("005930"))
			.andExpect(jsonPath("$.data.content[0].side").value("SELL"))
			.andExpect(jsonPath("$.data.content[0].realizedProfit").value(10000))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	private Trade mockTrade(Long tradeId, Long stockId) {
		Trade trade = mock(Trade.class);
		given(trade.getTradeId()).willReturn(tradeId);
		given(trade.getTradeNo()).willReturn(UUID.randomUUID());
		given(trade.getStockId()).willReturn(stockId);
		given(trade.getSide()).willReturn(OrderSide.SELL);
		given(trade.getQuantity()).willReturn(10);
		given(trade.getPrice()).willReturn(new BigDecimal("50000"));
		given(trade.getTotalAmount()).willReturn(new BigDecimal("500000"));
		given(trade.getFee()).willReturn(BigDecimal.ZERO);
		given(trade.getTax()).willReturn(BigDecimal.ZERO);
		given(trade.getRealizedProfit()).willReturn(new BigDecimal("10000"));
		given(trade.getCreatedAt()).willReturn(OffsetDateTime.now());
		return trade;
	}

	private Stock mockStock(Long stockId, String stockCode, String stockName) {
		Stock mockStock = mock(Stock.class);
		given(mockStock.getId()).willReturn(stockId);
		given(mockStock.getStockCode()).willReturn(stockCode);
		given(mockStock.getStockName()).willReturn(stockName);
		return mockStock;
	}

}