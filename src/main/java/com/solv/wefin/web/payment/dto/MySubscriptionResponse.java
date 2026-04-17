package com.solv.wefin.web.payment.dto;

import com.solv.wefin.domain.payment.dto.MySubscriptionInfo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MySubscriptionResponse(
        Long planId,
        String planName,
        BigDecimal price,
        String billingCycle,
        String description,
        String subscriptionStatus,
        boolean active,
        OffsetDateTime startedAt,
        OffsetDateTime expiredAt
) {
    public static MySubscriptionResponse from(MySubscriptionInfo info) {
        return new MySubscriptionResponse(
                info.planId(),
                info.planName(),
                info.price(),
                info.billingCycle().name(),
                info.description(),
                info.subscriptionStatus().name(),
                info.active(),
                info.startedAt(),
                info.expiredAt()
        );
    }
}