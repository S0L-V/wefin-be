package com.solv.wefin.domain.trading.matching.event;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.trading.order.dto.response.OrderMatchedNotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderMatchedEventListener {

	private final SimpMessagingTemplate messagingTemplate;
	private final OrderRepository orderRepository;
	private final VirtualAccountService virtualAccountService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleOrderMatchedEvent(OrderMatchedEvent event) {
		log.info("체결 완료 - orderNo: {}, side: {}, stockCode: {}, quantity: {}, price: {}",
			event.orderNo(), event.side(), event.stockCode(), event.quantity(), event.price());

		pushOrderMatchedNotification(event);

		// TODO: 채팅 알림 - 수익금 ±50만원 이상 매도 시 (별도 티켓)
	}

	/**
	 * 체결 알림을 해당 사용자의 WebSocket 큐로 push 한다.
	 *
	 * <p>실패 시 예외를 전파하지 않고 로그만 남긴다. 후속 리스너(채팅 알림 등)나
	 * 원 트랜잭션의 관점에서 push 실패는 치명적이지 않기 때문이다.
	 */
	private void pushOrderMatchedNotification(OrderMatchedEvent event) {
		try {
			UUID userId = resolveUserId(event.orderNo());
			OrderMatchedNotification notification = OrderMatchedNotification.from(event);
			messagingTemplate.convertAndSendToUser(
				userId.toString(),
				"/queue/orders",
				notification
			);
		} catch (Exception e) {
			log.error("체결 알림 전송 실패: orderNo={}", event.orderNo(), e);
		}
	}

	/**
	 * 주문번호로부터 사용자 ID를 조회한다.
	 * orderNo → Order → virtualAccountId → VirtualAccount → userId
	 */
	private UUID resolveUserId(UUID orderNo) {
		Order order = orderRepository.findByOrderNo(orderNo)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		VirtualAccount account = virtualAccountService.getAccount(order.getVirtualAccountId());
		return account.getUserId();
	}
}
