package com.solv.wefin.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT("잘못된 입력입니다.", 400),
    INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다.", 500),

    // Order
    ORDER_001("예수금이 부족합니다.", 400),

    // Market
    MARKET_001("종목을 찾을 수 없습니다.", 404);

    private final String message;
    private final int status;
}
