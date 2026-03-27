package com.solv.wefin.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(400, "잘못된 입력입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    // Order - BUY
    ORDER_INSUFFICIENT_BALANCE(400, "예수금이 부족합니다."),
    ORDER_INVALID_QUANTITY(400, "주문 수량은 1 이상이어야 합니다."),
    ORDER_STOCK_NOT_FOUND(400, "존재하지 않는 종목입니다."),
    ORDER_MARKET_CLOSED(400, "장 마감 시간에는 주문할 수 없습니다."),
    ORDER_INVALID_AMOUNT(400, "금액은 0보다 커야 합니다."),

    // Order - SELL
    ORDER_INSUFFICIENT_HOLDINGS(400, "보유 수량이 부족합니다."),
    ORDER_STOCK_NOT_HELD(400, "해당 종목을 보유하고 있지 않습니다."),

    // Order - MODIFY/CANCEL
    ORDER_ALREADY_FILLED(400, "이미 체결된 주문입니다."),

    // Account
    ACCOUNT_NOT_FOUND(404, "계좌를 찾을 수 없습니다."),
    ACCOUNT_ALREADY_EXISTS(400, "이미 계좌가 존재합니다."),

    // Market
    MARKET_001(404, "종목을 찾을 수 없습니다.");

    private final int status;
    private final String message;
}
