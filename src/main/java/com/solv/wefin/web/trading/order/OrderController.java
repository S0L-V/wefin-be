package com.solv.wefin.web.trading.order;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.dto.OrderCancelInfo;
import com.solv.wefin.domain.trading.order.dto.OrderInfo;
import com.solv.wefin.domain.trading.order.service.OrderService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.order.dto.request.OrderBuyRequest;
import com.solv.wefin.web.trading.order.dto.request.OrderModifyRequest;
import com.solv.wefin.web.trading.order.dto.request.OrderSellRequest;
import com.solv.wefin.web.trading.order.dto.response.OrderCancelResponse;
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

	@PutMapping("/{orderNo}")
	public ApiResponse<OrderResponse> modify(@AuthenticationPrincipal UUID userId,
											 @PathVariable UUID orderNo,
											 @Valid @RequestBody OrderModifyRequest request) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		OrderInfo info = orderService.modifyOrder(account.getVirtualAccountId(), orderNo,
			request.requestPrice(), request.quantity());
		OrderResponse response = OrderResponse.from(info);
		return ApiResponse.success(response);
	}

	@DeleteMapping("/{orderNo}")
	public ApiResponse<OrderCancelResponse> cancel(@AuthenticationPrincipal UUID userId,
												   @PathVariable UUID orderNo) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		OrderCancelInfo cancelInfo = orderService.cancelOrder(account.getVirtualAccountId(), orderNo);
		OrderCancelResponse response = OrderCancelResponse.from(cancelInfo);
		return ApiResponse.success(response);
	}
}
