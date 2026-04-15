package com.solv.wefin.web.trading.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderSellRequest(
	@NotBlank String stockCode,
	@NotNull @Min(1) Integer quantity
) {
}
