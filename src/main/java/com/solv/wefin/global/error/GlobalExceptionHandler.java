package com.solv.wefin.global.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.solv.wefin.global.common.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ApiResponse<Object> error = ApiResponse.error(errorCode);
        return ResponseEntity.status(errorCode.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidException(MethodArgumentNotValidException e) {
        ApiResponse<Object> error = ApiResponse.error(ErrorCode.INVALID_INPUT);
        return ResponseEntity.status(error.getStatus()).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleJsonParseException(HttpMessageNotReadableException e) {
        ApiResponse<Object> error = ApiResponse.error(ErrorCode.INVALID_INPUT);
        return ResponseEntity.status(error.getStatus()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error(e.getMessage(), e);
        ApiResponse<Object> error = ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(error.getStatus()).body(error);
    }

}
