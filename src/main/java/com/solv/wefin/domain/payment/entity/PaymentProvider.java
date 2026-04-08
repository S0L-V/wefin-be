package com.solv.wefin.domain.payment.entity;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;

import java.util.Arrays;

public enum PaymentProvider {
    TOSS;

    public static PaymentProvider from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PROVIDER));
    }
}