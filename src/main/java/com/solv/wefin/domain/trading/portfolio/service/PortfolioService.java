package com.solv.wefin.domain.trading.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.portfolio.dto.PortfolioInfo;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.repository.PortfolioRepository;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;
	private final StockInfoProvider stockInfoProvider;
	private final MarketPriceProvider marketPriceProvider;

	@Transactional
	public void addHolding(Long virtualAccountId, Long stockId, Integer quantity, BigDecimal price, Currency currency) {
		Optional<Portfolio> portfolio = findPortfolioForUpdate(virtualAccountId, stockId);

		if (portfolio.isPresent()) {
			portfolio.get().addQuantity(quantity, price);
		} else {
			portfolioRepository.save(new Portfolio(virtualAccountId, stockId, quantity, price, currency));
		}
	}

	@Transactional
	public void deductQuantity(Long virtualAccountId, Long stockId, Integer quantity) {
		Optional<Portfolio> portfolio = findPortfolioForUpdate(virtualAccountId, stockId);

		if (portfolio.isEmpty()) {
			throw new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD);
		}

		portfolio.get().deductQuantity(quantity);
		if (portfolio.get().getQuantity().equals(0)) {
			portfolioRepository.deleteById(portfolio.get().getPortfolioId());
		}
	}

	/**
	 * 포트폴리오 단건 조회 - 매도 시 보유종목 확인
	 */
	public Portfolio getPortfolio(Long virtualAccountId, Long stockId) {
		return portfolioRepository.findByVirtualAccountIdAndStockId(virtualAccountId, stockId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD));
	}

	/**
	 * 보유종목 목록 조회
	 */
	public List<Portfolio> getPortfolios(Long virtualAccountId) {
		return portfolioRepository.findByVirtualAccountId(virtualAccountId);
	}

	/**
	 * 보유종목 + 종목명 + 현재가 (API 응답용)
	 */
	public List<PortfolioInfo> getPortfolioInfos(Long virtualAccountId) {
		List<Portfolio> portfolios = portfolioRepository.findByVirtualAccountId(
			virtualAccountId);
		return portfolios.stream().map(p -> {
			Stock stock = stockInfoProvider.getStock(p.getStockId());
			BigDecimal currentPrice = marketPriceProvider.getCurrentPrice(stock.getStockCode());
			return new PortfolioInfo(p, stock.getStockCode(), stock.getStockName(), currentPrice);
		}).toList();
	}

	/**
	 * 비관적 락으로 포트폴리오 조회 - 없으면 예외
	 */
	public Portfolio getPortfolioForUpdate(Long virtualAccountId, Long stockId) {
		return portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(virtualAccountId, stockId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD));
	}

	/**
	 * 비관적 락으로 포트폴리오 조회 - Optional 반환
	 */
	private Optional<Portfolio> findPortfolioForUpdate(Long virtualAccountId, Long stockId) {
		return portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(virtualAccountId, stockId);
	}
}
