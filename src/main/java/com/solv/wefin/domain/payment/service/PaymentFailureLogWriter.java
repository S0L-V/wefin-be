package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentFailureLog;
import com.solv.wefin.domain.payment.repository.PaymentFailureLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFailureLogWriter {

    private final PaymentFailureLogRepository paymentFailureLogRepository;

    @Transactional
    public void save(
            Payment payment,
            User user,
            String orderId,
            String paymentKey,
            String stage,
            String errorCode,
            String errorMessage
    ) {
        PaymentFailureLog log = PaymentFailureLog.create(
                payment,
                user,
                orderId,
                paymentKey,
                stage,
                errorCode,
                errorMessage
        );

        paymentFailureLogRepository.save(log);
    }
}