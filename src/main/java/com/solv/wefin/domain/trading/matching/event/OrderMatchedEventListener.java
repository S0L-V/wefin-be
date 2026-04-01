package com.solv.wefin.domain.trading.matching.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderMatchedEventListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleOrderMatchedEvent(OrderMatchedEvent event) {
		log.info("체결 완료 - orderNo: {}, side: {}, stockCode: {}, quantity: {}, price: {}",
			event.orderNo(), event.side(), event.stockCode(), event.quantity(), event.price());

		// TODO: WebSocket 체결 알림 (스프린트 5)
		// TODO: 채팅 알림 - 수익금 +-50만원 이상 매도 시 (스프린트 5)
	}
}
