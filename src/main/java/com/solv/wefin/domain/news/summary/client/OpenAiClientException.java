package com.solv.wefin.domain.news.summary.client;

import org.springframework.http.HttpStatusCode;

/**
 * OpenAI Chat API 호출 자체의 실패(HTTP 오류, 네트워크 장애)를 나타내는 예외
 * 호출자가 재시도 가능한 transient 오류인지 exception 타입만으로 판단할 수 있게 한다.
 */
public class OpenAiClientException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public OpenAiClientException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
