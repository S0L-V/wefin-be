package com.solv.wefin.domain.payment.service;

import com.solv.wefin.domain.payment.dto.TossPaymentConfirmResult;
import com.solv.wefin.domain.payment.entity.TossPaymentStatus;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${toss.secret-key:}")
    private String secretKey;

    @Value("${toss.base-url:https://api.tosspayments.com}")
    private String baseUrl;

    public TossPaymentConfirmResult confirm(
            String paymentKey,
            String orderId,
            BigDecimal amount
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }

        RestTemplate restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodeSecretKey(secretKey));

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TossPaymentConfirmResponse> response = restTemplate.exchange(
                    baseUrl + "/v1/payments/confirm",
                    HttpMethod.POST,
                    requestEntity,
                    TossPaymentConfirmResponse.class
            );

            TossPaymentConfirmResponse responseBody = response.getBody();
            if (responseBody == null || responseBody.paymentKey() == null) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
            }

            return new TossPaymentConfirmResult(
                    responseBody.paymentKey(),
                    responseBody.orderId(),
                    TossPaymentStatus.valueOf(responseBody.status())
            );
        } catch (RestClientException e) {
            log.warn("Toss confirm failed. orderId={}, message={}", orderId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }

    private String encodeSecretKey(String secretKey) {
        String value = secretKey + ":";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record TossPaymentConfirmResponse(
            String paymentKey,
            String orderId,
            String status
    ) {
    }
}