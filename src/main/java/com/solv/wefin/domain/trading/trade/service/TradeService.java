package com.solv.wefin.domain.trading.trade.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.trade.entity.Trade;
import com.solv.wefin.domain.trading.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TradeService {

	private final TradeRepository tradeRepository;

	@Transactional
	public Trade createBuyTrade(Long orderId, Long virtualAccountId, Long stockId,
								Integer quantity, BigDecimal price, BigDecimal totalAmount,
								BigDecimal fee, Currency currency, BigDecimal exchangeRate) {
		return tradeRepository.save(Trade.createBuyTrade(orderId, virtualAccountId, stockId, quantity,
			price, totalAmount, fee, currency, exchangeRate));
	}

	@Transactional
	public Trade createSellTrade(Long orderId, Long virtualAccountId, Long stockId,
								 Integer quantity, BigDecimal price, BigDecimal totalAmount,
								 BigDecimal fee, BigDecimal tax, BigDecimal realizedProfit,
								 Currency currency, BigDecimal exchangeRate) {
		return tradeRepository.save(Trade.createSellTrade(orderId, virtualAccountId, stockId, quantity,
			price, totalAmount, fee, tax, realizedProfit, currency, exchangeRate));
	}
}
