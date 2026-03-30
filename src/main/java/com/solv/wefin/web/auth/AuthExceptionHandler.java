package com.solv.wefin.web.auth;

import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// global 보다 먼저 실행
@Order(Ordered.HIGHEST_PRECEDENCE)
// AuthController에서 발생한 예외에만 적용
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValidationException(MethodArgumentNotValidException e) {

        // 에러 순서 유지
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return new ApiResponse<>(
                ErrorCode.AUTH_VALIDATION_FAILED.getStatus(),
                ErrorCode.AUTH_VALIDATION_FAILED.name(),
                ErrorCode.AUTH_VALIDATION_FAILED.getMessage(),
                errors
        );
    }
}