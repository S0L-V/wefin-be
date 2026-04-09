package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderIdGenerator {

    private final PaymentRepository paymentRepository;

    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderId;
        do {
            String suffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();

            orderId = "ORDER-" + date + "-" + suffix;
        } while (paymentRepository.existsByOrderId(orderId));

        return orderId;
    }
}
