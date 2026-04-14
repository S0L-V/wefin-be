package com.solv.wefin.web.payment;

import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
import com.solv.wefin.domain.payment.service.PaymentService;
import com.solv.wefin.global.config.SecurityConfig;
import com.solv.wefin.global.config.security.JwtAuthenticationEntryPoint;
import com.solv.wefin.global.config.security.JwtAuthenticationFilter;
import com.solv.wefin.global.config.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class
})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("결제 준비 생성 요청에 성공한다")
    void createPayment_success() throws Exception {
        UUID userId = UUID.randomUUID();

        PaymentReadyInfo info = new PaymentReadyInfo(
                1L,
                "ORDER-20260408-AB12CD34",
                1L,
                "프리미엄 월간 이용권",
                "MONTHLY",
                new BigDecimal("9900"),
                "TOSS",
                "READY",
                OffsetDateTime.parse("2026-04-08T21:30:00+09:00")
        );

        given(paymentService.createPayment(eq(userId), any()))
                .willReturn(info);

        String requestBody = """
                {
                  "planId": 1,
                  "provider": "TOSS"
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        java.util.List.of()
                                )
                        ))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(1))
                .andExpect(jsonPath("$.data.orderId").value("ORDER-20260408-AB12CD34"))
                .andExpect(jsonPath("$.data.planId").value(1))
                .andExpect(jsonPath("$.data.planName").value("프리미엄 월간 이용권"))
                .andExpect(jsonPath("$.data.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.data.amount").value(9900))
                .andExpect(jsonPath("$.data.provider").value("TOSS"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.requestedAt").exists());
    }

    @Test
    @DisplayName("planId가 없으면 400을 반환한다")
    void createPayment_fail_whenPlanIdIsNull() throws Exception {
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                  "provider": "TOSS"
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        java.util.List.of()
                                )
                        ))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("provider가 비어 있으면 400을 반환한다")
    void createPayment_fail_whenProviderIsBlank() throws Exception {
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                  "planId": 1,
                  "provider": ""
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        java.util.List.of()
                                )
                        ))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("결제 승인 요청에 성공한다")
    void confirmPayment_success() throws Exception {
        UUID userId = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now();

        PaymentConfirmInfo info = new PaymentConfirmInfo(
                1L,
                "ORDER-20260414-12345",
                1L,
                "프리미엄 월간 이용권",
                "MONTHLY",
                new BigDecimal("9900"),
                "TOSS",
                "PAID",
                "pay_test_123",
                now,
                now,
                now.plusMonths(1)
        );

        given(paymentService.confirmPayment(
                eq(userId),
                eq("pay_test_123"),
                eq("ORDER-20260414-12345"),
                eq(new BigDecimal("9900"))
        )).willReturn(info);

        String requestBody = """
                {
                  "paymentKey": "pay_test_123",
                  "orderId": "ORDER-20260414-12345",
                  "amount": 9900
                }
                """;

        mockMvc.perform(post("/api/payments/confirm")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        java.util.List.of()
                                )
                        ))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(1))
                .andExpect(jsonPath("$.data.orderId").value("ORDER-20260414-12345"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.providerPaymentKey").value("pay_test_123"));
    }

    @Test
    @DisplayName("paymentKey가 없으면 400을 반환한다")
    void confirmPayment_fail_whenPaymentKeyIsBlank() throws Exception {
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                  "paymentKey": "",
                  "orderId": "ORDER-20260414-12345",
                  "amount": 9900
                }
                """;

        mockMvc.perform(post("/api/payments/confirm")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        java.util.List.of()
                                )
                        ))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}