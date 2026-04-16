package com.solv.wefin.domain.trading.market.dto;

import java.math.BigDecimal;

public record StockIndicatorInfo(
        BigDecimal per,      // 주가수익비율
        BigDecimal pbr,      // 주가순자산비율
        BigDecimal eps,      // 주당순이익
        BigDecimal roe       // 자기자본이익률 (%) — DART 기반 계산
) {
}
