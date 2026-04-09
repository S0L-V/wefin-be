package com.solv.wefin.domain.trading.snapshot.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;
import com.solv.wefin.domain.trading.snapshot.repository.DailySnapshotRepository;
import com.solv.wefin.domain.trading.stock.entity.Stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SnapshotPersistenceService {

	private final DailySnapshotRepository snapshotRepository;
	private final PortfolioService portfolioService;
	private final StockInfoProvider stockInfoProvider;
	private final MarketPriceProvider marketPriceProvider;

	/**
	 * 단일 계좌의 일별 자산 스냅샷을 생성
	 * 이미 존재하는 스킵, 시세 조회 실패 시 avgPrice 풀백
	 * REQUIRES_NEW 계좌별 독립 트랜잭션 보장
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean createSnapshot(VirtualAccount account, LocalDate date) {
		boolean hasSnapshot = snapshotRepository.existsByVirtualAccountIdAndSnapshotDate(
			account.getVirtualAccountId(), date);

		if (!hasSnapshot) {
			List<Portfolio> portfolios = portfolioService.getPortfolios(account.getVirtualAccountId());
			BigDecimal evaluationAmount = BigDecimal.ZERO;

			for (Portfolio portfolio : portfolios) {
				Stock stock = stockInfoProvider.getStock(portfolio.getStockId());
				if (stock == null) {
					log.warn("종목 정보 없음, avgPrice 풀백: stockId={}", portfolio.getStockId());
					BigDecimal avgPrice = resolveFallbackPrice(portfolio);
					evaluationAmount = evaluationAmount.add(BigDecimal.valueOf(
						portfolio.getQuantity()).multiply(avgPrice));
					continue;
				}

				BigDecimal currentPrice;
				try {
					currentPrice = marketPriceProvider.getCurrentPrice(stock.getStockCode());
				} catch (Exception e) {
					log.warn("시세 조회 실패, avgPrice 풀백: stockId={}, error={}", portfolio.getStockId(), e.getMessage());
					currentPrice = resolveFallbackPrice(portfolio);
				}
				evaluationAmount = evaluationAmount.add(BigDecimal.valueOf(portfolio.getQuantity())
					.multiply(currentPrice));
			}

			BigDecimal balance = account.getBalance();
			BigDecimal totalAsset = balance.add(evaluationAmount);
			BigDecimal realizedProfit = account.getTotalRealizedProfit();
			snapshotRepository.save(
				DailySnapshot.of(account.getVirtualAccountId(), date, totalAsset, balance,
					evaluationAmount, realizedProfit));
			return true;
		}

		return false;
	}

	private static BigDecimal resolveFallbackPrice(Portfolio portfolio) {
		BigDecimal avgPrice = portfolio.getAvgPrice();
		return (avgPrice != null) ? avgPrice : BigDecimal.ZERO;
	}
}
