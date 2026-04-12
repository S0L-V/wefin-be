package com.solv.wefin.web.game.order.dto.response;

import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.order.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderResponse {

    private UUID orderId;
    private String symbol;
    private String stockName;
    private OrderType orderType;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal tax;

    public static OrderResponse from(GameOrder order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getStockInfo().getSymbol(),
                order.getStockName(),
                order.getOrderType(),
                order.getQuantity(),
                order.getOrderPrice(),
                order.getFee(),
                order.getTax()
        );
    }
}
