package com.solv.wefin.domain.trading.indices.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 홈 상단 주요 지수 4종 정의.
 * Yahoo Finance 심볼과 지연 여부를 함께 보관한다.
 */
@Getter
@RequiredArgsConstructor
public enum IndexCode {
    KOSPI("^KS11", "코스피", false),
    KOSDAQ("^KQ11", "코스닥", false),
    NASDAQ("^IXIC", "나스닥", true),
    SP500("^GSPC", "S&P 500", true);

    private final String yahooSymbol;
    private final String label;
    private final boolean delayed;
}
