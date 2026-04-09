package com.solv.wefin.web.trading.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;
import com.solv.wefin.domain.trading.snapshot.service.SnapshotService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.account.dto.AccountResponse;
import com.solv.wefin.web.trading.account.dto.AssetHistoryResponse;
import com.solv.wefin.web.trading.account.dto.BuyingPowerResponse;

import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

	private final VirtualAccountService accountService;
	private final SnapshotService snapshotService;

	@GetMapping
	public ApiResponse<AccountResponse> getAccount(@AuthenticationPrincipal UUID userId) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		AccountResponse response = AccountResponse.from(account);
		return ApiResponse.success(response);
	}

	@GetMapping("/buying-power")
	public ApiResponse<BuyingPowerResponse> buyingPower(@AuthenticationPrincipal UUID userId,
														@RequestParam @DecimalMin(value = "0", inclusive = false) BigDecimal price) {
		VirtualAccount account = accountService.getAccountByUserId(userId);
		Integer quantity = accountService.calculateBuyingPower(account.getVirtualAccountId(), price);
		return ApiResponse.success(new BuyingPowerResponse(quantity));
	}

	@GetMapping("/asset-history")
	public ApiResponse<AssetHistoryResponse> getAssetHistory(@AuthenticationPrincipal UUID userId,
				@RequestParam(required = false)
				@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
				@RequestParam(required = false)
				@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

		VirtualAccount account = accountService.getAccountByUserId(userId);
		List<DailySnapshot> snapshots = snapshotService.getAssetHistory(account.getVirtualAccountId(), from, to);
		return ApiResponse.success(AssetHistoryResponse.from(snapshots));
	}
}

