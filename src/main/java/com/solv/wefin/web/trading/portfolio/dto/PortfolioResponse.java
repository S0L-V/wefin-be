package com.solv.wefin.web.trading.portfolio.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.solv.wefin.domain.trading.portfolio.dto.PortfolioInfo;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;

public record PortfolioResponse(
	String stockName,
	String stockCode,
	Integer quantity,
	BigDecimal avgPrice,
	BigDecimal currentPrice,
	BigDecimal evaluationAmount,
	BigDecimal profitLoss,
	BigDecimal profitRate
	) {

	public static PortfolioResponse from(PortfolioInfo info) {
		Portfolio portfolio = info.portfolio();
		BigDecimal currentPrice = info.currentPrice();
		BigDecimal evaluationAmount = currentPrice.multiply(BigDecimal.valueOf(portfolio.getQuantity()));
		BigDecimal profitLoss = currentPrice.subtract(portfolio.getAvgPrice())
			.multiply(BigDecimal.valueOf(portfolio.getQuantity()));
		BigDecimal invested = portfolio.getAvgPrice().multiply(BigDecimal.valueOf(portfolio.getQuantity()));
		BigDecimal profitRate;
		if (invested.compareTo(BigDecimal.ZERO) == 0) {
			profitRate = BigDecimal.ZERO;
		} else {
			profitRate = profitLoss.divide(invested, 4, RoundingMode.HALF_UP)
				.multiply(new BigDecimal("100"));
		}

		return new PortfolioResponse(
			info.stockName(),
			info.stockCode(),
			portfolio.getQuantity(),
			portfolio.getAvgPrice(),
			currentPrice,
			evaluationAmount,
			profitLoss, profitRate
		);
	}
}
