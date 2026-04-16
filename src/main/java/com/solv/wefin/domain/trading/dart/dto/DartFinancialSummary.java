package com.solv.wefin.domain.trading.dart.dto;

public record DartFinancialSummary(
        String businessYear,
        String reportCode,
        String currency,
        DartFinancialPeriod currentPeriod,
        DartFinancialPeriod previousPeriod,
        DartFinancialPeriod prePreviousPeriod
) {
}
