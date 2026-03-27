package com.solv.wefin.domain.trading.portfolio.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.repository.PortfolioRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

	@Mock
	private PortfolioRepository portfolioRepository;

	@InjectMocks
	private PortfolioService portfolioService;

	@Test
	void 신규_추가() {
		// given
		when(portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(1L, 1L))
			.thenReturn(Optional.empty());

		// when
		portfolioService.addHolding(1L, 1L, 10, new BigDecimal("100000"), Currency.KRW);

		// then
		verify(portfolioRepository).save(any());
	}

	@Test
	void 추가_매수() {
		// given
		Portfolio portfolio = new Portfolio(1L, 1L, 10, new BigDecimal("98000"), Currency.KRW);
		when(portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(1L, 1L))
			.thenReturn(Optional.of(portfolio));

		// when
		portfolioService.addHolding(1L, 1L, 5, new BigDecimal("134000"), Currency.KRW);

		// then
		assertThat(portfolio.getQuantity()).isEqualTo(15);
		assertThat(portfolio.getAvgPrice()).isEqualTo(new BigDecimal("110000.00"));
	}

	@Test
	void 매도_차감() {
		// given
		Portfolio portfolio = new Portfolio(1L, 1L, 10, new BigDecimal("98000"), Currency.KRW);
		when(portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(1L, 1L))
			.thenReturn(Optional.of(portfolio));

		// when
		portfolioService.deductQuantity(1L, 1L, 8);

		// then
		assertThat(portfolio.getQuantity()).isEqualTo(2);
	}

	@Test
	void 전량_매도_시_삭제() {
		// given
		Portfolio portfolio = new Portfolio(1L, 1L, 13, new BigDecimal("134000"), Currency.KRW);
		when(portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(1L, 1L))
			.thenReturn(Optional.of(portfolio));

		// when
		portfolioService.deductQuantity(1L, 1L, 13);

		// then
		verify(portfolioRepository).deleteById(any());
	}
}