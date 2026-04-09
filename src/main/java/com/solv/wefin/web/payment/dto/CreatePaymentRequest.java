package com.solv.wefin.web.payment.dto;

import com.solv.wefin.domain.payment.dto.CreatePaymentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotNull @Positive Long planId,
        @NotBlank String provider
) {
    public CreatePaymentCommand toCommand() {
        return new CreatePaymentCommand(planId, provider);
    }
}