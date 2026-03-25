package com.solv.wefin.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
