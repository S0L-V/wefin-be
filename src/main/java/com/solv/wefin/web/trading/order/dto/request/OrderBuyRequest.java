package com.solv.wefin.web.trading.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderBuyRequest(
	@NotNull Long stockId,
	@NotNull @Min(1) Integer quantity
) {
}
