package com.solv.wefin.web.trading.order.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderModifyRequest(
	@NotNull BigDecimal requestPrice,
	@NotNull @Min(1) Integer quantity
) {
}
