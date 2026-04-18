package com.solv.wefin.domain.game.order.dto;

import com.solv.wefin.domain.game.order.entity.OrderType;

public record OrderCommand(String symbol, OrderType orderType, Integer quantity) {
}
