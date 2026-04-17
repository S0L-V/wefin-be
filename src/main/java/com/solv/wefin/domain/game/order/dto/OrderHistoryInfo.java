package com.solv.wefin.domain.game.order.dto;

import com.solv.wefin.domain.game.order.entity.OrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderHistoryInfo(
        UUID orderId,
        Integer turnNumber,
        LocalDate turnDate,
        String symbol,
        String stockName,
        OrderType orderType,
        Integer quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax
) {
}
