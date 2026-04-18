package com.solv.wefin.domain.trading.matching.dev;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.matching.event.OrderMatchedEvent;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 개발 환경에서 OrderMatchedEvent를 수동으로 발행하기 위한 헬퍼.
 *
 * <p>장외 시간이나 한투 WebSocket 틱 없이 리스너 → WebSocket push 경로를
 * E2E로 검증할 때 사용한다. local profile에서만 활성화되므로 prod/dev/staging에는 노출되지 않는다.
 *
 * <p>사용 방법:
 * <ol>
 *   <li>실제로 주문(시장가/지정가 무관)을 생성해 orderNo 확보</li>
 *   <li>해당 orderNo를 이 simulator에 전달</li>
 *   <li>리스너가 이벤트를 수신 → WebSocket으로 해당 사용자의 /user/queue/orders 에 push</li>
 *   <li>프론트에서 구독 중이면 알림 수신</li>
 * </ol>
 */
@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class OrderMatchedEventSimulator {

	private static final BigDecimal FALLBACK_PRICE = new BigDecimal("70000");

	private final ApplicationEventPublisher eventPublisher;
	private final OrderRepository orderRepository;
	private final StockInfoProvider stockInfoProvider;
	private final VirtualAccountService virtualAccountService;

	/**
	 * 주어진 orderNo를 기반으로 OrderMatchedEvent를 구성해 발행한다.
	 *
	 * <p>@Transactional 블록 안에서 발행해야 AFTER_COMMIT phase의 리스너가 호출된다.
	 */
	@Transactional
	public void simulate(UUID orderNo) {
		Order order = orderRepository.findByOrderNo(orderNo)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Stock stock = stockInfoProvider.getStock(order.getStockId());
		VirtualAccount account = virtualAccountService.getAccount(order.getVirtualAccountId());

		OrderMatchedEvent event = buildEvent(order, stock, account);
		eventPublisher.publishEvent(event);

		log.info("[DEV] OrderMatchedEvent 시뮬레이션 발행 - orderNo: {}, side: {}, userId: {}",
			orderNo, order.getSide(), account.getUserId());
	}

	private OrderMatchedEvent buildEvent(Order order, Stock stock, VirtualAccount account) {
		BigDecimal price = order.getRequestPrice() != null
			? order.getRequestPrice()
			: FALLBACK_PRICE;
		OffsetDateTime matchedAt = OffsetDateTime.now();

		if (order.getSide() == OrderSide.BUY) {
			return OrderMatchedEvent.ofBuy(
				order.getOrderType(),
				order.getOrderNo(),
				stock.getStockCode(),
				stock.getStockName(),
				order.getQuantity(),
				price,
				order.getFee(),
				account.getBalance(),
				matchedAt
			);
		}

		return OrderMatchedEvent.ofSell(
			order.getOrderType(),
			order.getOrderNo(),
			stock.getStockCode(),
			stock.getStockName(),
			order.getQuantity(),
			price,
			order.getFee(),
			order.getTax(),
			BigDecimal.ZERO,
			account.getBalance(),
			matchedAt
		);
	}
}
