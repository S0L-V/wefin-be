package com.solv.wefin.domain.payment.dto;

import com.solv.wefin.domain.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentReadyInfo(
        Long paymentId,
        String orderId,
        Long planId,
        String planName,
        String billingCycle,
        BigDecimal amount,
        String provider,
        String status,
        OffsetDateTime requestedAt
) {
    public static PaymentReadyInfo from(Payment payment) {
        return new PaymentReadyInfo(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getPlan().getPlanId(),
                payment.getPlan().getPlanName(),
                payment.getPlan().getBillingCycle().name(),
                payment.getAmount(),
                payment.getProvider().name(),
                payment.getStatus().name(),
                payment.getRequestedAt()
        );
    }
}