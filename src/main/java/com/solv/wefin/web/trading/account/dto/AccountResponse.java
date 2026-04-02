package com.solv.wefin.web.trading.account.dto;

import java.math.BigDecimal;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;

public record AccountResponse(
	BigDecimal balance,
	BigDecimal initialBalance,
	BigDecimal totalRealizedProfit
) {

	public static AccountResponse from(VirtualAccount account) {
		return new AccountResponse(
			account.getBalance(),
			account.getInitialBalance(),
			account.getTotalRealizedProfit()
		);
	}
}
