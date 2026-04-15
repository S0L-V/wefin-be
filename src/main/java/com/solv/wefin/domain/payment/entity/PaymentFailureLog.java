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
@Table(name = "payment_failure_log")
public class PaymentFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_failure_log_id")
    private Long paymentFailureLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "stage", nullable = false, length = 50)
    private String stage;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    @Column(name = "logged_at", nullable = false)
    private OffsetDateTime loggedAt;

    private PaymentFailureLog(
            Payment payment,
            User user,
            String orderId,
            String paymentKey,
            String stage,
            String errorCode,
            String errorMessage
    ) {
        this.payment = payment;
        this.user = user;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.stage = stage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.loggedAt = OffsetDateTime.now();
    }

    public static PaymentFailureLog create(
            Payment payment,
            User user,
            String orderId,
            String paymentKey,
            String stage,
            String errorCode,
            String errorMessage
    ) {
        return new PaymentFailureLog(
                payment,
                user,
                orderId,
                paymentKey,
                stage,
                errorCode,
                errorMessage
        );
    }
}