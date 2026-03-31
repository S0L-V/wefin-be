package com.solv.wefin.domain.trading.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.repository.PortfolioRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;

	@Transactional
	public void addHolding(Long virtualAccountId, Long stockId, Integer quantity, BigDecimal price, Currency currency) {
		Optional<Portfolio> portfolio = getPortfolioForUpdate(virtualAccountId, stockId);

		if (portfolio.isPresent()) {
			portfolio.get().addQuantity(quantity, price);
		} else {
			portfolioRepository.save(new Portfolio(virtualAccountId, stockId, quantity, price, currency));
		}
	}

	@Transactional
	public void deductQuantity(Long virtualAccountId, Long stockId, Integer quantity) {
		Optional<Portfolio> portfolio = getPortfolioForUpdate(virtualAccountId, stockId);

		if (portfolio.isEmpty()) {
			throw new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD);
		}

		portfolio.get().deductQuantity(quantity);
		if (portfolio.get().getQuantity().equals(0)) {
			portfolioRepository.deleteById(portfolio.get().getPortfolioId());
		}
	}

	public Portfolio getPortfolio(Long virtualAccountId, Long stockId) {
		return portfolioRepository.findByVirtualAccountIdAndStockId(virtualAccountId, stockId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD));
	}

	public List<Portfolio> getPortfolios(Long virtualAccountId) {
		return portfolioRepository.findByVirtualAccountId(virtualAccountId);
	}

	/**
	 * 비관적 락으로 포트폴리오 조회
	 */
	private Optional<Portfolio> getPortfolioForUpdate(Long virtualAccountId, Long stockId) {
		return portfolioRepository.findByVirtualAccountIdAndStockIdForUpdate(virtualAccountId, stockId);
	}
}
