package com.solv.wefin.domain.payment.dto;

import com.solv.wefin.domain.payment.entity.TossPaymentStatus;

import java.time.OffsetDateTime;

public record TossPaymentConfirmResult(
        String paymentKey,
        String orderId,
        TossPaymentStatus status,
        OffsetDateTime approvedAt
) {
}