package com.solv.wefin.domain.trading.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderType;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.trade.entity.Trade;
import com.solv.wefin.domain.trading.trade.repository.TradeRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

	private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");

	private final OrderRepository orderRepository;
	private final TradeRepository tradeRepository;
	private final PortfolioService portfolioService;
	private final VirtualAccountService virtualAccountService;
	private final MarketPriceProvider marketPriceProvider;


	@Transactional
	public Order buyMarket(Long virtualAccountId, Long stockId, String stockCode, Integer quantity) {
		// 1. 수량 검증
		if (quantity == null || quantity <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}

		// 2. 현재가 조회
		BigDecimal currentPrice = marketPriceProvider.getCurrentPrice(stockCode);

		// 3. 금액 계산
		BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));

		// 4. 수수료 계산 (totalAmount x 0.00015, 절사)
		BigDecimal fee = totalAmount.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);

		// 5. 예수금 차감 (totalAmount + fee)
		virtualAccountService.deductBalance(virtualAccountId, totalAmount.add(fee));

		// 6. Order 생성 + 저장
		Order order = orderRepository.save(new Order(virtualAccountId, stockId, OrderType.MARKET, OrderSide.BUY, quantity,
			null, Currency.KRW, null, fee, BigDecimal.ZERO));

		// 7. Trade 생성 + 저장
		Trade trade = tradeRepository.save(Trade.createBuyTrade(order.getOrderId(), virtualAccountId, stockId,
			quantity, currentPrice, totalAmount, fee, Currency.KRW, null));
		order.fill(quantity);

		// 8. 포트폴리오 갱신
		portfolioService.addHolding(virtualAccountId, stockId, quantity, currentPrice, Currency.KRW);

		// 9. 반환
		return order;
	}
}
