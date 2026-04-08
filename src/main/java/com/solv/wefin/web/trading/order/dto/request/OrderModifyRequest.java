package com.solv.wefin.web.trading.order.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderModifyRequest(
	@NotNull @Positive BigDecimal requestPrice,
	@NotNull @Min(1) Integer quantity
) {
}
