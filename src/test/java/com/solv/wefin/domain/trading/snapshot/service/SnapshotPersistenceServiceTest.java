package com.solv.wefin.domain.trading.snapshot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;
import com.solv.wefin.domain.trading.snapshot.repository.DailySnapshotRepository;
import com.solv.wefin.domain.trading.stock.entity.Stock;

@ExtendWith(MockitoExtension.class)
class SnapshotPersistenceServiceTest {

	@Mock
	DailySnapshotRepository snapshotRepository;
	@Mock
	PortfolioService portfolioService;
	@Mock
	StockInfoProvider stockInfoProvider;
	@Mock
	MarketPriceProvider marketPriceProvider;

	@InjectMocks
	SnapshotPersistenceService snapshotPersistenceService;

	private VirtualAccount mockAccount;

	private static final LocalDate TEST_DATE = LocalDate.of(2026, 9, 6);

	@BeforeEach
	void setUp() {
		mockAccount = mock(VirtualAccount.class);
		given(mockAccount.getVirtualAccountId()).willReturn(1L);
	}

	@Test
	void 스냅샷_생성_성공() {
		// given
		given(mockAccount.getBalance()).willReturn(BigDecimal.valueOf(5000000));
		given(mockAccount.getTotalRealizedProfit()).willReturn(BigDecimal.valueOf(100000));

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getStockId()).willReturn(1L);
		given(mockPortfolio.getQuantity()).willReturn(10);

		Stock mockStock = mock(Stock.class);
		given(mockStock.getStockCode()).willReturn("005930");

		given(snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(1L, TEST_DATE))
			.willReturn(false);
		given(portfolioService.getPortfolios(1L)).willReturn(List.of(mockPortfolio));
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(marketPriceProvider.getCurrentPrice("005930")).willReturn(BigDecimal.valueOf(50000));

		// when
		boolean result = snapshotPersistenceService.createSnapshot(mockAccount, TEST_DATE);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		DailySnapshot saved = captor.getValue();
		// 50000 x 10 = 500,000 + balance 5,000,000 = 5,500,000
		assertThat(saved.getTotalAsset()).isEqualByComparingTo(BigDecimal.valueOf(5500000));
		assertThat(saved.getEvaluationAmount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
	}

	@Test
	void 이미_존재하면_스킵() {
		// given
		given(snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(1L, TEST_DATE))
			.willReturn(true);

		// when
		boolean result = snapshotPersistenceService.createSnapshot(mockAccount, TEST_DATE);

		// then
		assertThat(result).isFalse();
		verify(snapshotRepository, never()).save(any());
	}

	@Test
	void 보유종목_없는_계좌() {
		// given
		given(mockAccount.getBalance()).willReturn(BigDecimal.valueOf(10000000));
		given(mockAccount.getTotalRealizedProfit()).willReturn(BigDecimal.ZERO);

		given(snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(1L, TEST_DATE))
			.willReturn(false);
		given(portfolioService.getPortfolios(1L)).willReturn(List.of());

		// when
		boolean result = snapshotPersistenceService.createSnapshot(mockAccount, TEST_DATE);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		DailySnapshot saved = captor.getValue();
		// 보유종목 없음 -> evaluationAmount = 0, totalAsset=balance = 10,000,000
		assertThat(saved.getTotalAsset()).isEqualByComparingTo(BigDecimal.valueOf(10000000));
		assertThat(saved.getEvaluationAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void 시세_조회_실패_avgPrice_풀백() {
		// given
		given(mockAccount.getBalance()).willReturn(BigDecimal.valueOf(5000000));
		given(mockAccount.getTotalRealizedProfit()).willReturn(BigDecimal.ZERO);

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getStockId()).willReturn(1L);
		given(mockPortfolio.getQuantity()).willReturn(10);
		given(mockPortfolio.getAvgPrice()).willReturn(BigDecimal.valueOf(40000));

		Stock mockStock = mock(Stock.class);
		given(mockStock.getStockCode()).willReturn("005930");

		given(snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(1L, TEST_DATE))
			.willReturn(false);
		given(portfolioService.getPortfolios(1L)).willReturn(List.of(mockPortfolio));
		given(stockInfoProvider.getStock(1L)).willReturn(mockStock);
		given(marketPriceProvider.getCurrentPrice("005930")).willThrow(new RuntimeException("API 실패"));

		// when
		boolean result = snapshotPersistenceService.createSnapshot(mockAccount, TEST_DATE);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		DailySnapshot saved = captor.getValue();
		// avgPrice = 40000 x quantity = 10 = 400,000 + balance = 5,000,000 = 5,400,000
		assertThat(saved.getTotalAsset()).isEqualByComparingTo(BigDecimal.valueOf(5400000));
		assertThat(saved.getEvaluationAmount()).isEqualByComparingTo(BigDecimal.valueOf(400000));
	}

	@Test
	void 종목정보_없으면_avgPrice_풀백() {
		// given
		given(mockAccount.getBalance()).willReturn(BigDecimal.valueOf(5000000));
		given(mockAccount.getTotalRealizedProfit()).willReturn(BigDecimal.ZERO);

		Portfolio mockPortfolio = mock(Portfolio.class);
		given(mockPortfolio.getStockId()).willReturn(1L);
		given(mockPortfolio.getQuantity()).willReturn(10);
		given(mockPortfolio.getAvgPrice()).willReturn(BigDecimal.valueOf(30000));

		given(snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(
			mockAccount.getVirtualAccountId(), TEST_DATE)).willReturn(false);
		given(portfolioService.getPortfolios(1L)).willReturn(List.of(mockPortfolio));
		given(stockInfoProvider.getStock(1L)).willReturn(null);

		// when
		boolean result = snapshotPersistenceService.createSnapshot(mockAccount, TEST_DATE);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		DailySnapshot saved = captor.getValue();
		// 30000 x 10 = 300,000
		assertThat(saved.getTotalAsset()).isEqualByComparingTo(BigDecimal.valueOf(5300000));
		assertThat(saved.getEvaluationAmount()).isEqualByComparingTo(BigDecimal.valueOf(300000));
	}
}