package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.*;
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
        PaymentLockedInfo lockedInfo =
                paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);

        TossPaymentConfirmResult result;

        log.info("confirm API 진입 userId={}, orderId={}", userId, orderId);

        try {
            log.info("confirm start: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);

            result = tossPaymentClient.confirm(
                    paymentKey,
                    orderId,
                    amount
            );

            log.info("confirm success: result={}", result);

        } catch (BusinessException e) {
            paymentConfirmWriter.saveFailedAfterConfirmApiError(
                    lockedInfo.paymentId(),
                    paymentKey,
                    e.getErrorCode().name(),
                    e.getMessage()
            );
            throw e;
        }

        if (result.status() == TossPaymentStatus.FAILED) {
            paymentConfirmWriter.saveFailedAfterConfirmResult(
                    lockedInfo.paymentId(),
                    paymentKey,
                    "TOSS_FAILED",
                    ErrorCode.PAYMENT_CONFIRM_FAILED.name(),
                    "Toss payment status is FAILED"
            );
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }

        if (result.status() == TossPaymentStatus.CANCELED) {
            paymentConfirmWriter.saveCanceledAfterConfirmResult(
                    lockedInfo.paymentId(),
                    paymentKey,
                    "TOSS_CANCELED",
                    ErrorCode.PAYMENT_CANCELED.name(),
                    "Toss payment status is CANCELED"
            );
            throw new BusinessException(ErrorCode.PAYMENT_CANCELED);
        }

        try {
            return paymentConfirmWriter.saveConfirmedPayment(
                    lockedInfo.paymentId(),
                    paymentKey,
                    result
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            paymentConfirmWriter.saveFailureAfterConfirmSaveError(
                    lockedInfo.paymentId(),
                    paymentKey,
                    e.getMessage()
            );

            try {
                tossPaymentClient.cancel(result.paymentKey(), "INTERNAL_ERROR");
            } catch (Exception cancelException) {
                log.error("Payment cancel failed after confirm success. paymentKey={}", result.paymentKey(), cancelException);

                paymentConfirmWriter.saveCancelFailureLog(
                        lockedInfo.paymentId(),
                        paymentKey,
                        cancelException.getMessage()
                );
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public MySubscriptionInfo getMySubscription(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserUserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_NOT_FOUND));

        return MySubscriptionInfo.from(subscription);
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
}