package com.solv.wefin.domain.trading.dart.dto;

import java.math.BigDecimal;

public record DartFinancialPeriod(
        String periodName,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal revenue,
        BigDecimal operatingIncome,
        BigDecimal netIncome
) {
}
