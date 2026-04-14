package com.solv.wefin.web.payment.dto;

import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ConfirmPaymentResponse(
        Long paymentId,
        String orderId,
        Long planId,
        String planName,
        String billingCycle,
        BigDecimal amount,
        String provider,
        String status,
        String providerPaymentKey,
        OffsetDateTime approvedAt,
        OffsetDateTime subscriptionStartedAt,
        OffsetDateTime subscriptionExpiredAt
) {
    public static ConfirmPaymentResponse from(PaymentConfirmInfo info) {
        return new ConfirmPaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.planId(),
                info.planName(),
                info.billingCycle(),
                info.amount(),
                info.provider(),
                info.status(),
                info.providerPaymentKey(),
                info.approvedAt(),
                info.subscriptionStartedAt(),
                info.subscriptionExpiredAt()
        );
    }
}