package com.solv.wefin.global.common;

import com.solv.wefin.global.error.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ApiResponse<T> {

    private final int status;
    private final String code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, null, null, data);
    }

    //201 요청 때
    public static <T> ApiResponse<T> success(int status, T data) {
        return new ApiResponse<>(status, null, null, data);
    }

    public static <T> ApiResponse<T> error(ErrorCode code) {
        return new ApiResponse<>(code.getStatus(), code.name(), code.getMessage(), null);
    }
}
