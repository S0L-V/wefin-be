package com.solv.wefin.domain.trading.portfolio.dto;

import java.math.BigDecimal;

import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;

public record PortfolioInfo(
	Portfolio portfolio,
	String stockCode,
	String stockName,
	BigDecimal currentPrice
) {
}
