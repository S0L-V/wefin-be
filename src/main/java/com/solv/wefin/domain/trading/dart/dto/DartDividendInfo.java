package com.solv.wefin.domain.trading.dart.dto;

import java.math.BigDecimal;

public record DartDividendInfo(
        String businessYear,                  // 조회 사업연도
        BigDecimal dividendPerShare,          // 주당 현금배당금 (원, 보통주 당기)
        BigDecimal dividendYieldRate,         // 현금배당수익률 (%, 당기)
        BigDecimal payoutRatio                // 현금배당성향 (%, 당기)
) {
}
