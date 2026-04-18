package com.solv.wefin.web.payment;

import com.solv.wefin.domain.payment.dto.MySubscriptionInfo;
import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
import com.solv.wefin.domain.payment.service.PaymentService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.payment.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<CreatePaymentResponse> createPayment(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        PaymentReadyInfo info = paymentService.createPayment(
                userId,
                request.toCommand()
        );

        return ApiResponse.success(CreatePaymentResponse.from(info));
    }

    @PostMapping("/confirm")
    public ApiResponse<ConfirmPaymentResponse> confirmPayment(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        PaymentConfirmInfo info = paymentService.confirmPayment(
                userId,
                request.paymentKey(),
                request.orderId(),
                request.amount()
        );

        return ApiResponse.success(ConfirmPaymentResponse.from(info));
    }

    @GetMapping("/me/subscription")
    public ApiResponse<MySubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal UUID userId
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        MySubscriptionInfo info = paymentService.getMySubscription(userId);

        return ApiResponse.success(MySubscriptionResponse.from(info));
    }
}