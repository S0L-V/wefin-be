package com.solv.wefin.web.trading.order.dev;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.matching.dev.OrderMatchedEventSimulator;

import lombok.RequiredArgsConstructor;

/**
 * 로컬 개발 환경 전용: OrderMatchedEvent 수동 발행 엔드포인트.
 *
 * <p>{@code @Profile("local")} 로만 활성화되어 prod/dev/staging 환경에서는 빈 등록되지 않는다.
 *
 * <p>사용 예:
 * <pre>
 * POST /api/dev/order-matched/simulate/{orderNo}
 * </pre>
 */
@RestController
@Profile("local")
@RequestMapping("/api/dev/order-matched")
@RequiredArgsConstructor
public class OrderMatchedEventDevController {

	private final OrderMatchedEventSimulator simulator;

	@PostMapping("/simulate/{orderNo}")
	public ResponseEntity<Void> simulate(@PathVariable UUID orderNo) {
		simulator.simulate(orderNo);
		return ResponseEntity.ok().build();
	}
}
