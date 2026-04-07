package com.solv.wefin.web.trading.order;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.solv.wefin.global.config.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.dto.OrderInfo;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderStatus;
import com.solv.wefin.domain.trading.order.entity.OrderType;
import com.solv.wefin.domain.trading.order.service.OrderService;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;
	@MockitoBean
	private VirtualAccountService accountService;
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
	void 매수_성공() throws Exception {
		// given
		Order mockOrder = mock(Order.class);
		given(mockOrder.getOrderNo()).willReturn(UUID.randomUUID());
		given(mockOrder.getSide()).willReturn(OrderSide.BUY);
		given(mockOrder.getOrderType()).willReturn(OrderType.MARKET);
		given(mockOrder.getQuantity()).willReturn(10);
		given(mockOrder.getStatus()).willReturn(OrderStatus.FILLED);
		given(mockOrder.getFee()).willReturn(new BigDecimal("146"));
		given(mockOrder.getCreatedAt()).willReturn(OffsetDateTime.now());

		OrderInfo mockOrderInfo = new OrderInfo(mockOrder, "005930", "삼성전자",
			new BigDecimal("178000"), new BigDecimal("9024854"),
			BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("9024854"));

		given(orderService.buyMarket(anyLong(), anyLong(), anyInt()))
			.willReturn(mockOrderInfo);

		// when & then
		mockMvc.perform(post("/api/order/buy")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockId\": 1, \"quantity\": 10}")
				.with(csrf())
				.with(SecurityMockMvcRequestPostProcessors.authentication(
					new UsernamePasswordAuthenticationToken(testUserId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.side").value("BUY"))
			.andExpect(jsonPath("$.data.stockCode").value("005930"))
			.andExpect(jsonPath("$.data.quantity").value(10));
	}

	@Test
	void 매도_성공() throws Exception {
		// given
		Order mockOrder = mock(Order.class);
		given(mockOrder.getOrderNo()).willReturn(UUID.randomUUID());
		given(mockOrder.getSide()).willReturn(OrderSide.SELL);
		given(mockOrder.getOrderType()).willReturn(OrderType.MARKET);
		given(mockOrder.getQuantity()).willReturn(10);
		given(mockOrder.getStatus()).willReturn(OrderStatus.FILLED);
		given(mockOrder.getFee()).willReturn(new BigDecimal("146"));
		given(mockOrder.getCreatedAt()).willReturn(OffsetDateTime.now());

		OrderInfo mockOrderInfo = new OrderInfo(mockOrder, "005930", "삼성전자",
			new BigDecimal("178000"), new BigDecimal("9013931"),
			new BigDecimal("3204"), new BigDecimal("80000"), new BigDecimal("9024582"));

		given(orderService.sellMarket(anyLong(), anyLong(), anyInt()))
			.willReturn(mockOrderInfo);

		// when & then
		mockMvc.perform(post("/api/order/sell")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockId\": 1, \"quantity\": 10}")
				.with(csrf())
				.with(SecurityMockMvcRequestPostProcessors.authentication(
					new UsernamePasswordAuthenticationToken(testUserId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.side").value("SELL"))
			.andExpect(jsonPath("$.data.tax").value(3204))
			.andExpect(jsonPath("$.data.realizedProfit").value(80000))
			.andExpect(jsonPath("$.data.stockCode").value("005930"))
			.andExpect(jsonPath("$.data.quantity").value(10));
	}

}