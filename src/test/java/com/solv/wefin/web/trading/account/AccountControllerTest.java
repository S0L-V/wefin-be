package com.solv.wefin.web.trading.account;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.solv.wefin.global.config.security.JwtProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VirtualAccountService accountService;

	@MockitoBean
	private JwtProvider jwtProvider;

	private UUID testUserId;
	@BeforeEach
	void setUp() {
		testUserId = UUID.randomUUID();
	}

	@Test
	void 계좌_조회_성공() throws Exception {
		// given
		VirtualAccount mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getBalance()).willReturn(new BigDecimal("10000000"));
		given(mockAccount.getInitialBalance()).willReturn(new BigDecimal("10000000"));
		given(mockAccount.getTotalRealizedProfit()).willReturn(BigDecimal.ZERO);
		given(accountService.getAccountByUserId(any())).willReturn(mockAccount);

		// when & then
		mockMvc.perform(get("/api/account")
				.with(SecurityMockMvcRequestPostProcessors.authentication(
					new UsernamePasswordAuthenticationToken(testUserId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.balance").value(10000000));
	}

	@Test
	void 주문가능수량_조회_성공() throws Exception {
		// given
		VirtualAccount mockAccount = mock(VirtualAccount.class);
		given(accountService.getAccountByUserId(any())).willReturn(mockAccount);
		given(mockAccount.getVirtualAccountId()).willReturn(1L);
		given(accountService.calculateBuyingPower(anyLong(), any())).willReturn(100);

		// when & then
		mockMvc.perform(get("/api/account/buying-power")
				.param("price", "186000")
				.with(SecurityMockMvcRequestPostProcessors.authentication(
					new UsernamePasswordAuthenticationToken(testUserId, null, List.of())
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.maxQuantity").value(100));
	}
}