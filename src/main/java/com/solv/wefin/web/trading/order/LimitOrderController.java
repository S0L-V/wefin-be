package com.solv.wefin.web.trading.order;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.order.dto.OrderInfo;
import com.solv.wefin.domain.trading.order.service.LimitOrderService;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.order.dto.request.LimitOrderBuyRequest;
import com.solv.wefin.web.trading.order.dto.request.LimitOrderSellRequest;
import com.solv.wefin.web.trading.order.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/order/limit")
@RequiredArgsConstructor
public class LimitOrderController {

    private final LimitOrderService limitOrderService;
    private final VirtualAccountService accountService;
    private final StockInfoProvider stockInfoProvider;

    @PostMapping("/buy")
    public ApiResponse<OrderResponse> buy(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody LimitOrderBuyRequest request
    ) {
        VirtualAccount account = accountService.getAccountByUserId(userId);
        Stock stock = stockInfoProvider.getStockByCode(request.stockCode());
        OrderInfo orderInfo = limitOrderService.buyLimit(
                account.getVirtualAccountId(),
                stock.getId(),
                request.quantity(),
                request.requestPrice()
        );
        return ApiResponse.success(OrderResponse.from(orderInfo));
    }

    @PostMapping("/sell")
    public ApiResponse<OrderResponse> sell(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody LimitOrderSellRequest request
    ) {
        VirtualAccount account = accountService.getAccountByUserId(userId);
        Stock stock = stockInfoProvider.getStockByCode(request.stockCode());
        OrderInfo orderInfo = limitOrderService.sellLimit(
                account.getVirtualAccountId(),
                stock.getId(),
                request.quantity(),
                request.requestPrice()
        );
        return ApiResponse.success(OrderResponse.from(orderInfo));
    }
}
