package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.order.dto.OrderHistoryInfo;
import com.solv.wefin.domain.game.order.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderHistoryResponse {

    private UUID orderId;
    private Integer turnNumber;
    private LocalDate turnDate;
    private String symbol;
    private String stockName;
    private OrderType orderType;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal tax;

    public static OrderHistoryResponse from(OrderHistoryInfo info) {
        return new OrderHistoryResponse(
                info.orderId(),
                info.turnNumber(),
                info.turnDate(),
                info.symbol(),
                info.stockName(),
                info.orderType(),
                info.quantity(),
                info.price(),
                info.fee(),
                info.tax());
    }
}
