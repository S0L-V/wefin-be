package com.solv.wefin.domain.payment.dto;

public record TossPaymentConfirmResult(
        String paymentKey,
        String orderId,
        String status
) {
}