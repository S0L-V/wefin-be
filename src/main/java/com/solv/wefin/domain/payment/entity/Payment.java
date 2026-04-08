package com.solv.wefin.domain.payment.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id")
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider;

    @Column(name = "provider_payment_key", length = 200)
    private String providerPaymentKey;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Payment(
            SubscriptionPlan plan,
            User user,
            String orderId,
            PaymentProvider provider,
            BigDecimal amount
    ) {
        this.plan = plan;
        this.user = user;
        this.orderId = orderId;
        this.provider = provider;
        this.amount = amount;
        this.status = PaymentStatus.READY;

        OffsetDateTime now = OffsetDateTime.now();
        this.requestedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment createReady(
            SubscriptionPlan plan,
            User user,
            String orderId,
            PaymentProvider provider,
            BigDecimal amount
    ) {
        return new Payment(plan, user, orderId, provider, amount);
    }

    public void markPaid(String providerPaymentKey) {
        this.providerPaymentKey = providerPaymentKey;
        this.status = PaymentStatus.PAID;
        this.approvedAt = OffsetDateTime.now();
        this.updatedAt = this.approvedAt;
        this.failedAt = null;
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = PaymentStatus.FAILED;
        this.failedAt = OffsetDateTime.now();
        this.updatedAt = this.failedAt;
        this.failureReason = failureReason;
    }

    public void markCanceled() {
        this.status = PaymentStatus.CANCELED;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isReady() {
        return this.status == PaymentStatus.READY;
    }

    public boolean isOwnedBy(java.util.UUID userId) {
        return this.user.getUserId().equals(userId);
    }
}