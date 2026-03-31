package com.solv.wefin.domain.trading.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderType;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.domain.trading.portfolio.entity.Currency;
import com.solv.wefin.domain.trading.portfolio.entity.Portfolio;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.trade.service.TradeService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

	private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");
	private static final BigDecimal TAX_RATE = new BigDecimal("0.0018");

	private final OrderRepository orderRepository;
	private final PortfolioService portfolioService;
	private final VirtualAccountService virtualAccountService;
	private final MarketPriceProvider marketPriceProvider;
	private final StockInfoProvider stockInfoProvider;
	private final TradeService tradeService;

	@Transactional
	public Order buyMarket(Long virtualAccountId, Long stockId, Integer quantity) {
		// 1. 수량 검증
		if (quantity == null || quantity <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}

		// 2. 현재가 조회
		Stock stock = stockInfoProvider.getStock(stockId);
		BigDecimal currentPrice = marketPriceProvider.getCurrentPrice(stock.getStockCode());
		if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.ORDER_STOCK_NOT_FOUND);
		}

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
		tradeService.createBuyTrade(order.getOrderId(), virtualAccountId, stockId,
			quantity, currentPrice, totalAmount, fee, Currency.KRW, null);
		order.fill(quantity);

		// 8. 포트폴리오 갱신
		portfolioService.addHolding(virtualAccountId, stockId, quantity, currentPrice, Currency.KRW);

		// 9. 반환
		return order;
	}

	@Transactional
	public Order sellMarket(Long virtualAccountId, Long stockId, Integer quantity) {
		// 1. 수량 검증
		if (quantity == null || quantity <= 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_QUANTITY);
		}

		// 2. 종목 조회
		Stock stock = stockInfoProvider.getStock(stockId);

		// 3. 현재가 조회
		BigDecimal currentPrice = marketPriceProvider.getCurrentPrice(stock.getStockCode());
		if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.ORDER_STOCK_NOT_FOUND);
		}

		// 4. 보유 종목 확인
		Portfolio portfolio = portfolioService.getPortfolio(virtualAccountId, stockId);
		if (portfolio.getQuantity() < quantity) {
			throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_HOLDINGS);
		}
		BigDecimal avgPrice = portfolio.getAvgPrice();

		// 5. 금액 계산
		BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));

		// 6. 수수료 계산
		BigDecimal fee = totalAmount.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);

		// 7. 매도 세금 계산
		BigDecimal tax = totalAmount.multiply(TAX_RATE).setScale(0, RoundingMode.DOWN);

		// 8. 실현손익 계산
		BigDecimal realizedAmount = currentPrice.subtract(avgPrice).multiply(BigDecimal.valueOf(quantity));

		// 9. Order 생성 + 저장
		Order order = orderRepository.save(
			new Order(virtualAccountId, stockId, OrderType.MARKET, OrderSide.SELL, quantity
				, null, Currency.KRW, null, fee, tax));

		// 10. Trade 생성 + 저장
		tradeService.createSellTrade(order.getOrderId(), virtualAccountId, stockId, quantity, currentPrice,
			totalAmount, fee, tax, realizedAmount, Currency.KRW, null);

		// 11. Order 상태 변경
		order.fill(quantity);

		// 12. 포트폴리오 수량 차감
		portfolioService.deductQuantity(virtualAccountId, stockId, quantity);

		// 13. 예수금 입금
		virtualAccountService.depositBalance(virtualAccountId, totalAmount.subtract(fee).subtract(tax));

		// 14. 실현손익 누적
		virtualAccountService.addRealizedProfit(virtualAccountId, realizedAmount);

		return order;
	}
}
