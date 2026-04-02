package com.solv.wefin.web.trading.portfolio;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.portfolio.dto.PortfolioInfo;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PortfolioService portfolioService;
	@MockitoBean
	private VirtualAccountService accountService;

	@Test
	@WithMockUser
	void 포트폴리오_조회_성공() throws Exception {
		// given
		VirtualAccount mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getVirtualAccountId()).willReturn(1L);
		given(accountService.getAccountByUserId(any())).willReturn(mockAccount);

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getQuantity()).willReturn(10);
		given(mockPortfolio.getAvgPrice()).willReturn(new BigDecimal("184000"));

		PortfolioInfo info = new PortfolioInfo(mockPortfolio, "005930", "삼성전자", new BigDecimal("184000"));
		given(portfolioService.getPortfolioInfos(1L)).willReturn(List.of(info));

		// when & then
		mockMvc.perform(get("/api/portfolio"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].stockCode").value("005930"))
			.andExpect(jsonPath("$.data[0].quantity").value(10));
	}
}