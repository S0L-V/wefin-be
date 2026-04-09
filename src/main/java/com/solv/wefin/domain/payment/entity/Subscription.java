package com.solv.wefin.domain.payment.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Subscription(
            SubscriptionPlan plan,
            User user,
            OffsetDateTime startedAt,
            OffsetDateTime expiredAt
    ) {
        this.plan = plan;
        this.user = user;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.expiredAt = expiredAt;
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    public static Subscription createActive(
            SubscriptionPlan plan,
            User user,
            OffsetDateTime startedAt,
            OffsetDateTime expiredAt
    ) {
        return new Subscription(plan, user, startedAt, expiredAt);
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE;
    }
}