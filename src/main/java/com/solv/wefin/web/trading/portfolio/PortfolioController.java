package com.solv.wefin.web.trading.portfolio;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.portfolio.dto.PortfolioInfo;
import com.solv.wefin.domain.trading.portfolio.service.PortfolioService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.portfolio.dto.PortfolioResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

	private static final UUID TEMP_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");

	private final PortfolioService portfolioService;
	private final VirtualAccountService accountService;

	@GetMapping
	public ApiResponse<List<PortfolioResponse>> getPortfolios() {
		VirtualAccount account = accountService.getAccountByUserId(TEMP_USER_ID);
		List<PortfolioInfo> infos = portfolioService.getPortfolioInfos(account.getVirtualAccountId());
		List<PortfolioResponse> response = infos.stream()
			.map(PortfolioResponse::from)
			.toList();
		return ApiResponse.success(response);
	}
}
