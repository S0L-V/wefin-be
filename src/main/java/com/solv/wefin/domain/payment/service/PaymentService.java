package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.CreatePaymentCommand;
import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
import com.solv.wefin.domain.payment.dto.TossPaymentConfirmResult;
import com.solv.wefin.domain.payment.entity.*;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionPlanRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentWriter paymentWriter;
    private final PaymentConfirmWriter paymentConfirmWriter;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentFailureLogWriter paymentFailureLogWriter;

    @Transactional
    public PaymentReadyInfo createPayment(UUID userId, CreatePaymentCommand command) {
        PaymentProvider provider = PaymentProvider.from(command.provider());

        SubscriptionPlan plan = subscriptionPlanRepository.findById(command.planId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        if (!plan.isAvailable()) {
            throw new BusinessException(ErrorCode.PLAN_INACTIVE);
        }

        boolean hasActiveSubscription = subscriptionRepository.existsByUserUserIdAndStatus(
                userId,
                SubscriptionStatus.ACTIVE
        );

        if (hasActiveSubscription) {
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);
        }

        Optional<Payment> existingReady =
                paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                        userId,
                        plan.getPlanId(),
                        provider,
                        PaymentStatus.READY
                );

        if (existingReady.isPresent()) {
            return PaymentReadyInfo.from(existingReady.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Payment saved = savePaymentWithRetry(plan, user, provider);

        return PaymentReadyInfo.from(saved);
    }

    public PaymentConfirmInfo confirmPayment(
            UUID userId,
            String paymentKey,
            String orderId,
            BigDecimal amount
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

        TossPaymentConfirmResult result;

        try {
            result = tossPaymentClient.confirm(
                    paymentKey,
                    orderId,
                    amount
            );
        } catch (BusinessException e) {
            payment.markFailed("TOSS_API_ERROR");
            paymentConfirmWriter.savePaidPaymentAndSubscription(payment, null);
            paymentFailureLogWriter.save(
                    payment,
                    payment.getUser(),
                    orderId,
                    paymentKey,
                    "CONFIRM_API",
                    e.getErrorCode().name(),
                    e.getMessage()
            );
            throw e;
        }

        if (result.status() == TossPaymentStatus.FAILED) {
            payment.markFailed("TOSS_FAILED");
            paymentConfirmWriter.savePaidPaymentAndSubscription(payment, null);
            paymentFailureLogWriter.save(
                    payment,
                    payment.getUser(),
                    orderId,
                    paymentKey,
                    "CONFIRM_RESULT",
                    ErrorCode.PAYMENT_CONFIRM_FAILED.name(),
                    "Toss payment status is FAILED"
            );
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }

        if (result.status() == TossPaymentStatus.CANCELED) {
            payment.markCanceled();
            paymentConfirmWriter.savePaidPaymentAndSubscription(payment, null);
            paymentFailureLogWriter.save(
                    payment,
                    payment.getUser(),
                    orderId,
                    paymentKey,
                    "CONFIRM_RESULT",
                    ErrorCode.PAYMENT_CANCELED.name(),
                    "Toss payment status is CANCELED"
            );
            throw new BusinessException(ErrorCode.PAYMENT_CANCELED);
        }

        payment.markPaid(result.paymentKey(), result.approvedAt());

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

        Subscription savedSubscription;
        try {
            savedSubscription =
                    paymentConfirmWriter.savePaidPaymentAndSubscription(payment, subscription);
        } catch (Exception e) {
            paymentFailureLogWriter.save(
                    payment,
                    payment.getUser(),
                    orderId,
                    paymentKey,
                    "SAVE_AFTER_CONFIRM",
                    ErrorCode.INTERNAL_SERVER_ERROR.name(),
                    e.getMessage()
            );

            try {
                tossPaymentClient.cancel(result.paymentKey(), "INTERNAL_ERROR");
            } catch (Exception cancelException) {
                log.error("Payment cancel failed after confirm success. paymentKey={}", result.paymentKey(), cancelException);

                paymentFailureLogWriter.save(
                        payment,
                        payment.getUser(),
                        orderId,
                        paymentKey,
                        "CANCEL_AFTER_CONFIRM",
                        ErrorCode.PAYMENT_CANCEL_FAILED.name(),
                        cancelException.getMessage()
                );
            }
            throw e;
        }

        return PaymentConfirmInfo.from(payment, savedSubscription);
    }

    private Payment savePaymentWithRetry(
            SubscriptionPlan plan,
            User user,
            PaymentProvider provider
    ) {
        for (int i = 0; i < 3; i++) {
            try {
                return paymentWriter.saveReadyPayment(plan, user, provider);
            } catch (DataIntegrityViolationException e) {
                Optional<Payment> concurrentReady =
                        paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                                user.getUserId(),
                                plan.getPlanId(),
                                provider,
                                PaymentStatus.READY
                        );

                if (concurrentReady.isPresent()) {
                    return concurrentReady.get();
                }

                if (i == 2) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
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