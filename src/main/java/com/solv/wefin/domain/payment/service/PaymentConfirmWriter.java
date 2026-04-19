package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentLockedInfo;
import com.solv.wefin.domain.payment.dto.TossPaymentConfirmResult;
import com.solv.wefin.domain.payment.entity.BillingCycle;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentConfirmWriter {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentFailureLogWriter paymentFailureLogWriter;

    @Transactional
    public PaymentLockedInfo loadAndValidateReadyPayment(
            UUID userId,
            String orderId,
            BigDecimal amount,
            String paymentKey
    ) {
        Payment payment = paymentRepository.findWithLockByOrderIdAndUserUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }

        if (!payment.isReady()) {
            paymentFailureLogWriter.save(
                    payment,
                    payment.getUser(),
                    orderId,
                    paymentKey,
                    "PRE_CONFIRM_VALIDATION",
                    ErrorCode.PAYMENT_NOT_READY.name(),
                    ErrorCode.PAYMENT_NOT_READY.getMessage()
            );
            throw new BusinessException(ErrorCode.PAYMENT_NOT_READY);
        }

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        boolean hasActiveSubscription = subscriptionRepository.existsByUserUserIdAndStatus(
                userId,
                SubscriptionStatus.ACTIVE
        );

        if (hasActiveSubscription) {
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);
        }

        return new PaymentLockedInfo(payment.getPaymentId());
    }

    @Transactional
    public void saveFailedAfterConfirmApiError(
            Long paymentId,
            String paymentKey,
            String errorCode,
            String errorMessage
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isPaid()) {
            payment.markFailed("TOSS_API_ERROR");
            paymentRepository.save(payment);
        }

        paymentFailureLogWriter.save(
                payment,
                payment.getUser(),
                payment.getOrderId(),
                paymentKey,
                "CONFIRM_API",
                errorCode,
                errorMessage
        );
    }

    @Transactional
    public void saveFailedAfterConfirmResult(
            Long paymentId,
            String paymentKey,
            String failureReason,
            String errorCode,
            String errorMessage
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isPaid()) {
            payment.markFailed(failureReason);
            paymentRepository.save(payment);
        }

        paymentFailureLogWriter.save(
                payment,
                payment.getUser(),
                payment.getOrderId(),
                paymentKey,
                "CONFIRM_RESULT",
                errorCode,
                errorMessage
        );
    }

    @Transactional
    public void saveCanceledAfterConfirmResult(
            Long paymentId,
            String paymentKey,
            String failureReason,
            String errorCode,
            String errorMessage
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isPaid()) {
            payment.markCanceled(failureReason);
            paymentRepository.save(payment);
        }

        paymentFailureLogWriter.save(
                payment,
                payment.getUser(),
                payment.getOrderId(),
                paymentKey,
                "CONFIRM_RESULT",
                errorCode,
                errorMessage
        );
    }

    @Transactional
    public PaymentConfirmInfo saveConfirmedPayment(
            Long paymentId,
            String paymentKey,
            TossPaymentConfirmResult result
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }

        if (!payment.isReady()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_READY);
        }

        boolean hasActiveSubscription = subscriptionRepository.existsByUserUserIdAndStatus(
                payment.getUser().getUserId(),
                SubscriptionStatus.ACTIVE
        );

        if (hasActiveSubscription) {
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);
        }

        payment.markPaid(paymentKey, result.approvedAt());

        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime expiredAt = calculateExpiredAt(
                startedAt,
                payment.getPlan().getBillingCycle()
        );

        Subscription subscription = Subscription.createActive(
                payment.getPlan(),
                payment.getUser(),
                startedAt,
                expiredAt
        );

        paymentRepository.save(payment);

        try {
            Subscription savedSubscription = subscriptionRepository.save(subscription);
            return PaymentConfirmInfo.from(payment, savedSubscription);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void saveFailureAfterConfirmSaveError(
            Long paymentId,
            String paymentKey,
            String errorMessage
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        paymentFailureLogWriter.save(
                payment,
                payment.getUser(),
                payment.getOrderId(),
                paymentKey,
                "SAVE_AFTER_CONFIRM",
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                errorMessage
        );
    }

    @Transactional
    public void saveCancelFailureLog(
            Long paymentId,
            String paymentKey,
            String errorMessage
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        paymentFailureLogWriter.save(
                payment,
                payment.getUser(),
                payment.getOrderId(),
                paymentKey,
                "CANCEL_AFTER_CONFIRM",
                ErrorCode.PAYMENT_CANCEL_FAILED.name(),
                errorMessage
        );
    }

    private OffsetDateTime calculateExpiredAt(
            OffsetDateTime startedAt,
            BillingCycle billingCycle
    ) {
        return switch (billingCycle) {
            case MONTHLY -> startedAt.plusMonths(1);
            case YEARLY -> startedAt.plusYears(1);
        };
    }
}