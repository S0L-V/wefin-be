package com.solv.wefin.domain.payment.dto;

import com.solv.wefin.domain.payment.entity.BillingCycle;
import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MySubscriptionInfo(
        Long planId,
        String planName,
        BigDecimal price,
        BillingCycle billingCycle,
        String description,
        SubscriptionStatus subscriptionStatus,
        boolean active,
        OffsetDateTime startedAt,
        OffsetDateTime expiredAt
) {
    public static MySubscriptionInfo from(Subscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();

        return new MySubscriptionInfo(
                plan.getPlanId(),
                plan.getPlanName(),
                plan.getPrice(),
                plan.getBillingCycle(),
                plan.getDescription(),
                subscription.getStatus(),
                subscription.isActive(),
                subscription.getStartedAt(),
                subscription.getExpiredAt()
        );
    }
}