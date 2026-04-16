package com.solv.wefin.domain.trading.matching.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.entity.Order;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.order.entity.OrderType;
import com.solv.wefin.domain.trading.order.repository.OrderRepository;
import com.solv.wefin.web.trading.order.dto.response.OrderMatchedNotification;

@ExtendWith(MockitoExtension.class)
class OrderMatchedEventListenerTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;
	@Mock
	private OrderRepository orderRepository;
	@Mock
	private VirtualAccountService virtualAccountService;

	@InjectMocks
	private OrderMatchedEventListener listener;

	private OrderMatchedEvent sampleEvent(UUID orderNo) {
		return OrderMatchedEvent.ofBuy(
			OrderType.LIMIT,
			orderNo,
			"005930",
			"삼성전자",
			10,
			BigDecimal.valueOf(50000),
			BigDecimal.valueOf(75),
			BigDecimal.valueOf(9499925),
			OffsetDateTime.now()
		);
	}

	@Nested
	class HandleOrderMatchedEventTest {

		@Test
		void 체결_알림을_사용자_큐로_전송한다() {
			// given
			UUID orderNo = UUID.randomUUID();
			UUID userId = UUID.randomUUID();
			Long virtualAccountId = 1L;

			OrderMatchedEvent event = sampleEvent(orderNo);

			Order mockOrder = mock(Order.class);
			given(mockOrder.getVirtualAccountId()).willReturn(virtualAccountId);
			given(orderRepository.findByOrderNo(orderNo)).willReturn(Optional.of(mockOrder));

			VirtualAccount mockAccount = mock(VirtualAccount.class);
			given(mockAccount.getUserId()).willReturn(userId);
			given(virtualAccountService.getAccount(virtualAccountId)).willReturn(mockAccount);

			// when
			listener.handleOrderMatchedEvent(event);

			// then
			ArgumentCaptor<OrderMatchedNotification> captor =
				ArgumentCaptor.forClass(OrderMatchedNotification.class);
			verify(messagingTemplate).convertAndSendToUser(
				eq(userId.toString()),
				eq("/queue/orders"),
				captor.capture()
			);

			OrderMatchedNotification sent = captor.getValue();
			assertThat(sent.orderNo()).isEqualTo(orderNo);
			assertThat(sent.stockCode()).isEqualTo("005930");
			assertThat(sent.side()).isEqualTo(OrderSide.BUY);
			assertThat(sent.orderType()).isEqualTo(OrderType.LIMIT);
			assertThat(sent.quantity()).isEqualTo(10);
			assertThat(sent.price()).isEqualByComparingTo(BigDecimal.valueOf(50000));
			assertThat(sent.matchedAt()).isNotNull();
		}

		@Test
		void 주문을_찾지_못하면_전송없이_예외를_삼킨다() {
			// given
			UUID orderNo = UUID.randomUUID();
			OrderMatchedEvent event = sampleEvent(orderNo);

			given(orderRepository.findByOrderNo(orderNo)).willReturn(Optional.empty());

			// when & then — 예외 전파 없이 정상 complete
			assertThatCode(() -> listener.handleOrderMatchedEvent(event))
				.doesNotThrowAnyException();

			verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
		}

		@Test
		void 메시징_전송_실패해도_예외를_삼킨다() {
			// given
			UUID orderNo = UUID.randomUUID();
			UUID userId = UUID.randomUUID();
			Long virtualAccountId = 1L;

			OrderMatchedEvent event = sampleEvent(orderNo);

			Order mockOrder = mock(Order.class);
			given(mockOrder.getVirtualAccountId()).willReturn(virtualAccountId);
			given(orderRepository.findByOrderNo(orderNo)).willReturn(Optional.of(mockOrder));

			VirtualAccount mockAccount = mock(VirtualAccount.class);
			given(mockAccount.getUserId()).willReturn(userId);
			given(virtualAccountService.getAccount(virtualAccountId)).willReturn(mockAccount);

			doThrow(new MessagingException("broker down"))
				.when(messagingTemplate)
				.convertAndSendToUser(anyString(), anyString(), any());

			// when & then — 예외 전파 없이 정상 complete
			assertThatCode(() -> listener.handleOrderMatchedEvent(event))
				.doesNotThrowAnyException();
		}
	}
}
