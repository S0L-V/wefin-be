package com.solv.wefin.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ErrorResponse {

    private final String code;
    private final String message;
    private final int status;

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errorCode.getStatus());
    }
}
