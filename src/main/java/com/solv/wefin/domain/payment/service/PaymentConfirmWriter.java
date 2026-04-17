package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentConfirmWriter {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Subscription savePaidPaymentAndSubscription(
            Payment payment,
            Subscription subscription
    ) {
        paymentRepository.save(payment);

        if (subscription == null) {
            return null;
        }

        return subscriptionRepository.save(subscription);
    }
}
