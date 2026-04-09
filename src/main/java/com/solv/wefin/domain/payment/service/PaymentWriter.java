package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentWriter {
    private final PaymentRepository paymentRepository;
    private final OrderIdGenerator orderIdGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment saveReadyPayment(
            SubscriptionPlan plan,
            User user,
            PaymentProvider provider
    ) {
        String orderId = orderIdGenerator.generate();

        Payment payment = Payment.createReady(
                plan,
                user,
                orderId,
                provider,
                plan.getPrice()
        );

        return paymentRepository.saveAndFlush(payment);
    }
}