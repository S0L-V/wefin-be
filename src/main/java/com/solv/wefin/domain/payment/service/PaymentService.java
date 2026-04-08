package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.CreatePaymentCommand;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.PaymentStatus;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionPlanRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrderIdGenerator orderIdGenerator;

    @Transactional
    public PaymentReadyInfo createPayment(UUID userId, CreatePaymentCommand command) {

        PaymentProvider provider = PaymentProvider.from(command.provider());

        SubscriptionPlan plan = subscriptionPlanRepository.findById(command.planId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        if (!plan.isAvailable()) {
            throw new BusinessException(ErrorCode.PLAN_INACTIVE);
        }

        boolean hasActiveSubscription =
                subscriptionRepository.existsByUserUserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                );

        if (hasActiveSubscription) {
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);
        }

        Optional<Payment> existingReady =
                paymentRepository.findTopByUserUserIdAndPlanPlanIdAndStatusOrderByRequestedAtDesc(
                        userId,
                        plan.getPlanId(),
                        PaymentStatus.READY
                );

        if (existingReady.isPresent()) {
            return PaymentReadyInfo.from(existingReady.get());
        }

        String orderId = orderIdGenerator.generate();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Payment payment = Payment.createReady(
                plan,
                user,
                orderId,
                provider,
                plan.getPrice()
        );

        Payment saved = paymentRepository.save(payment);

        return PaymentReadyInfo.from(saved);
    }
}