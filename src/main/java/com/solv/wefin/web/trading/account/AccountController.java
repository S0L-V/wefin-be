package com.solv.wefin.web.trading.account;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.account.dto.AccountResponse;
import com.solv.wefin.web.trading.account.dto.BuyingPowerResponse;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

	private final VirtualAccountService accountService;

	@GetMapping
	public ApiResponse<AccountResponse> getAccount(@AuthenticationPrincipal UUID userId) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		AccountResponse response = AccountResponse.from(account);
		return ApiResponse.success(response);
	}

	@GetMapping("/buying-power")
	public ApiResponse<BuyingPowerResponse> buyingPower(@AuthenticationPrincipal UUID userId,
														@RequestParam @Min(1) BigDecimal price) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		Integer quantity = accountService.calculateBuyingPower(account.getVirtualAccountId(), price);
		return ApiResponse.success(new BuyingPowerResponse(quantity));
	}
}

