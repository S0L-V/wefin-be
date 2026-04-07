package com.solv.wefin.web.trading.order;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.dto.OrderInfo;
import com.solv.wefin.domain.trading.order.service.OrderService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.order.dto.request.OrderBuyRequest;
import com.solv.wefin.web.trading.order.dto.request.OrderSellRequest;
import com.solv.wefin.web.trading.order.dto.response.OrderResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;
	private final VirtualAccountService accountService;

	@PostMapping("/buy")
	public ApiResponse<OrderResponse> buy(@AuthenticationPrincipal UUID userId,
										  @Valid @RequestBody OrderBuyRequest request) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		OrderInfo orderInfo = orderService.buyMarket(account.getVirtualAccountId(),
			request.stockId(), request.quantity());
		OrderResponse response = OrderResponse.from(orderInfo);
		return ApiResponse.success(response);
	}

	@PostMapping("/sell")
	public ApiResponse<OrderResponse> sell(@AuthenticationPrincipal UUID userId,
										   @Valid @RequestBody OrderSellRequest request) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		OrderInfo orderInfo = orderService.sellMarket(account.getVirtualAccountId(),
			request.stockId(), request.quantity());
		OrderResponse response = OrderResponse.from(orderInfo);
		return ApiResponse.success(response);
	}
}
