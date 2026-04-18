package com.solv.wefin.web.trading.order.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.solv.wefin.domain.trading.order.dto.OrderCancelInfo;

public record OrderCancelResponse(
	UUID orderNo,
	String status,
	BigDecimal refundedAmount,
	OffsetDateTime cancelledAt
) {

	public static OrderCancelResponse from(OrderCancelInfo info) {
		return new OrderCancelResponse(
			info.order().getOrderNo(),
			info.order().getStatus().name(),
			info.refundedAmount(),
			info.order().getCancelledAt()
		);
	}
}
