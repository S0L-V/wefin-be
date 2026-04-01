package com.solv.wefin.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // User
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),

    // Chat
    CHAT_MESSAGE_EMPTY(400, "메시지 내용은 비어 있을 수 없습니다."),
    CHAT_MESSAGE_TOO_LONG(400, "메시지는 1000자를 초과할 수 없습니다."),
    CHAT_SPAM_DETECTED(429, "메시지를 너무 빠르게 전송하고 있습니다."),

    // Common
    INVALID_INPUT(400, "잘못된 입력입니다."),
    DUPLICATE_RESOURCE(409,"중복된 리소스입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    // Auth - SIGNUP
    AUTH_EMAIL_DUPLICATED(409, "이미 사용 중인 이메일입니다."),
    AUTH_NICKNAME_DUPLICATED(409, "이미 사용 중인 닉네임입니다."),
    AUTH_VALIDATION_FAILED(400, "잘못된 입력입니다."),

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
    MARKET_STOCK_NOT_FOUND(404, "종목을 찾을 수 없습니다."),
    MARKET_API_FAILED(503, "한투 API 호출에 실패했습니다."),

    // Interest
    INTEREST_ALREADY_EXISTS(400, "이미 등록된 관심종목입니다."),
    INTEREST_LIMIT_EXCEEDED(400, "관심종목은 최대 10개까지 등록할 수 있습니다."),

    // Embedding
    EMBEDDING_ARTICLE_NOT_FOUND(500, "임베딩 대상 기사를 찾을 수 없습니다."),

    // Tagging
    TAGGING_ARTICLE_NOT_FOUND(500, "태깅 대상 기사를 찾을 수 없습니다."),
    TAGGING_ALREADY_RUNNING(409, "태깅 생성이 이미 실행 중입니다."),

    // GameRoom
    ROOM_NOT_FOUND(404,"게임장을 찾을 수 없습니다."),
    ROOM_ALREADY_EXISTS(409, "이미 진행 중이거나 대기 중인 게임방이 있습니다."),
    ROOM_HOST_ALREADY_EXISTS(409, "이미 방장으로 참여 중인 게임방이 있습니다."),
    ROOM_HOST_DAILY_LIMIT(409, "하루 방 생성 가능 횟수 초과"),
    ROOM_ALREADY_JOINED(409, "이미 참가 중인 방입니다."),
    ROOM_FULL(400, "인원 초과"),
    ROOM_FINISHED(400, "종료된 방입니다."),
    ROOM_NOT_PARTICIPANT(404, "참가자가 아닙니다.");

    private final int status;
    private final String message;
}
