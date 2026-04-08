package com.solv.wefin.domain.payment.dto;

public record CreatePaymentCommand (
        Long planId,
        String provider
) {
}
