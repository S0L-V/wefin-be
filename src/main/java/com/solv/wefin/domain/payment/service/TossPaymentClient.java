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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
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

            TossPaymentStatus status;
            try {
                status = TossPaymentStatus.valueOf(responseBody.status());
            } catch (IllegalArgumentException e) {
                log.warn("Unexpected Toss payment status. orderId={}, status={}", orderId, responseBody.status());
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
            }

            return new TossPaymentConfirmResult(
                    responseBody.paymentKey(),
                    responseBody.orderId(),
                    status,
                    responseBody.approvedAt()
            );
        } catch (ResourceAccessException e) {
            log.warn("Toss confirm timeout. orderId={}, message={}", orderId, e.getMessage());

            if (e.getCause() instanceof SocketTimeoutException) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_TIMEOUT);
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        } catch (HttpStatusCodeException e) {
            log.warn("Toss confirm http error. orderId={}, status={}, body={}",
                    orderId, e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_UNAUTHORIZED);
                }
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_BAD_REQUEST);
            }

            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        } catch (RestClientException e) {
            log.warn("Toss confirm failed. orderId={}, message={}", orderId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }

    public void cancel(
            String paymentKey,
            String cancelReason
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }

        RestTemplate restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodeSecretKey(secretKey));

        Map<String, Object> body = Map.of(
                "cancelReason", cancelReason
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TossPaymentCancelResponse> response = restTemplate.exchange(
                    baseUrl + "/v1/payments/" + paymentKey + "/cancel",
                    HttpMethod.POST,
                    requestEntity,
                    TossPaymentCancelResponse.class
            );

            TossPaymentCancelResponse responseBody = response.getBody();
            if (responseBody == null || responseBody.paymentKey() == null) {
                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
            }
        } catch (ResourceAccessException e) {
            log.warn("Toss cancel timeout. paymentKey={}, message={}", paymentKey, e.getMessage());

            if (e.getCause() instanceof SocketTimeoutException) {
                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_TIMEOUT);
            }
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        } catch (HttpStatusCodeException e) {
            log.warn("Toss cancel http error. paymentKey={}, status={}, body={}",
                    paymentKey, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        } catch (RestClientException e) {
            log.warn("Toss cancel failed. paymentKey={}, message={}", paymentKey, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }
    }

    private String encodeSecretKey(String secretKey) {
        String value = secretKey + ":";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record TossPaymentConfirmResponse(
            String paymentKey,
            String orderId,
            String status,
            OffsetDateTime approvedAt
    ) {
    }

    private record TossPaymentCancelResponse(
            String paymentKey,
            String status
    ) {
    }
}