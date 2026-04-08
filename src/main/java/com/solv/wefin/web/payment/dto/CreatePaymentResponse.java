package com.solv.wefin.web.payment.dto;

import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreatePaymentResponse(
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
    public static CreatePaymentResponse from(PaymentReadyInfo info) {
        return new CreatePaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.planId(),
                info.planName(),
                info.billingCycle(),
                info.amount(),
                info.provider(),
                info.status(),
                info.requestedAt()
        );
    }
}