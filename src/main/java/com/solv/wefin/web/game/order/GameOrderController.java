package com.solv.wefin.web.game.order;

import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.order.dto.OrderCommand;
import com.solv.wefin.domain.game.order.service.GameOrderService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.order.dto.request.OrderRequest;
import com.solv.wefin.web.game.order.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/orders")
@RequiredArgsConstructor
public class GameOrderController {

    private final GameOrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody OrderRequest request) {

        OrderCommand command = new OrderCommand(
                request.getSymbol(), request.getOrderType(), request.getQuantity());

        GameOrder order = orderService.placeOrder(roomId, userId, command);

        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, response));
    }
}
