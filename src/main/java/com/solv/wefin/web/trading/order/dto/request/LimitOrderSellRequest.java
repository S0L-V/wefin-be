package com.solv.wefin.web.trading.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LimitOrderSellRequest(
        @NotNull Long stockId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Positive BigDecimal requestPrice
) {
}

