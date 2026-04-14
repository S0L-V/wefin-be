package com.solv.wefin.domain.payment.dto;

import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.Subscription;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentConfirmInfo(
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
    public static PaymentConfirmInfo from(Payment payment, Subscription subscription) {
        return new PaymentConfirmInfo(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getPlan().getPlanId(),
                payment.getPlan().getPlanName(),
                payment.getPlan().getBillingCycle().name(),
                payment.getAmount(),
                payment.getProvider().name(),
                payment.getStatus().name(),
                payment.getProviderPaymentKey(),
                payment.getApprovedAt(),
                subscription.getStartedAt(),
                subscription.getExpiredAt()
        );
    }
}