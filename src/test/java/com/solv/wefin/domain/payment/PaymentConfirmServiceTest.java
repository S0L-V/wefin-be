package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentLockedInfo;
import com.solv.wefin.domain.payment.dto.TossPaymentConfirmResult;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.TossPaymentStatus;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionPlanRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.domain.payment.service.PaymentConfirmWriter;
import com.solv.wefin.domain.payment.service.PaymentService;
import com.solv.wefin.domain.payment.service.PaymentWriter;
import com.solv.wefin.domain.payment.service.TossPaymentClient;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentWriter paymentWriter;

    @Mock
    private PaymentConfirmWriter paymentConfirmWriter;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("결제 승인 성공 시 승인 결과를 반환한다")
    void confirmPayment_success() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        PaymentConfirmInfo expected = new PaymentConfirmInfo(
                paymentId,
                orderId,
                1L,
                "월간 이용권",
                "MONTHLY",
                amount,
                PaymentProvider.TOSS.name(),
                "PAID",
                paymentKey,
                approvedAt,
                approvedAt,
                approvedAt.plusMonths(1)
        );

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(new TossPaymentConfirmResult(
                        paymentKey,
                        orderId,
                        TossPaymentStatus.DONE,
                        approvedAt
                ));

        given(paymentConfirmWriter.saveConfirmedPayment(paymentId, paymentKey,
                new TossPaymentConfirmResult(paymentKey, orderId, TossPaymentStatus.DONE, approvedAt)))
                .willReturn(expected);

        PaymentConfirmInfo result = paymentService.confirmPayment(userId, paymentKey, orderId, amount);

        assertThat(result).isEqualTo(expected);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);
        verify(tossPaymentClient).confirm(paymentKey, orderId, amount);
        verify(paymentConfirmWriter).saveConfirmedPayment(
                eq(paymentId),
                eq(paymentKey),
                argThat(r ->
                        r.paymentKey().equals(paymentKey)
                                && r.orderId().equals(orderId)
                                && r.status() == TossPaymentStatus.DONE
                                && r.approvedAt().equals(approvedAt)
                )
        );
        verify(paymentConfirmWriter, never()).saveFailedAfterConfirmApiError(anyLong(), anyString(), anyString(), anyString());
        verify(paymentConfirmWriter, never()).saveFailedAfterConfirmResult(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(paymentConfirmWriter, never()).saveCanceledAfterConfirmResult(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(paymentConfirmWriter, never()).saveFailureAfterConfirmSaveError(anyLong(), anyString(), anyString());
        verify(paymentConfirmWriter, never()).saveCancelFailureLog(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("사전 검증에서 PAYMENT_NOT_FOUND가 발생하면 그대로 예외를 던진다")
    void confirmPayment_fail_whenPaymentNotFound() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-NOT-FOUND";
        BigDecimal amount = new BigDecimal("9900");

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);
        verifyNoInteractions(tossPaymentClient);
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("타인의 주문번호로 요청해도 PAYMENT_NOT_FOUND 예외가 발생한다")
    void confirmPayment_fail_whenOrderDoesNotBelongToUser() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-OTHER";
        BigDecimal amount = new BigDecimal("9900");

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);
        verifyNoInteractions(tossPaymentClient);
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("READY 상태가 아니면 PAYMENT_NOT_READY 예외가 발생한다")
    void confirmPayment_fail_whenPaymentNotReady() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_READY));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_READY);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);
        verifyNoInteractions(tossPaymentClient);
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("결제 금액이 다르면 PAYMENT_AMOUNT_MISMATCH 예외가 발생한다")
    void confirmPayment_fail_whenAmountMismatch() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";

        given(paymentConfirmWriter.loadAndValidateReadyPayment(
                userId,
                orderId,
                new BigDecimal("10000"),
                paymentKey
        )).willThrow(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThatThrownBy(() -> paymentService.confirmPayment(
                userId,
                paymentKey,
                orderId,
                new BigDecimal("10000")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(
                userId,
                orderId,
                new BigDecimal("10000"),
                paymentKey
        );
        verifyNoInteractions(tossPaymentClient);
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("이미 활성 구독이 있으면 ACTIVE_SUBSCRIPTION_ALREADY_EXISTS 예외가 발생한다")
    void confirmPayment_fail_whenActiveSubscriptionExists() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willThrow(new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);

        verify(paymentConfirmWriter).loadAndValidateReadyPayment(userId, orderId, amount, paymentKey);
        verifyNoInteractions(tossPaymentClient);
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("토스 confirm API에서 예외가 발생하면 실패 저장 후 예외를 다시 던진다")
    void confirmPayment_fail_whenTossConfirmApiThrows() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED);

        verify(tossPaymentClient).confirm(paymentKey, orderId, amount);
        verify(paymentConfirmWriter).saveFailedAfterConfirmApiError(
                paymentId,
                paymentKey,
                ErrorCode.PAYMENT_CONFIRM_FAILED.name(),
                ErrorCode.PAYMENT_CONFIRM_FAILED.getMessage()
        );
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("토스 상태가 FAILED면 실패 저장 후 PAYMENT_CONFIRM_FAILED 예외를 던진다")
    void confirmPayment_fail_whenTossStatusFailed() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-123";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(new TossPaymentConfirmResult(
                        paymentKey,
                        orderId,
                        TossPaymentStatus.FAILED,
                        approvedAt
                ));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED);

        verify(paymentConfirmWriter).saveFailedAfterConfirmResult(
                paymentId,
                paymentKey,
                "TOSS_FAILED",
                ErrorCode.PAYMENT_CONFIRM_FAILED.name(),
                "Toss payment status is FAILED"
        );
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("토스 상태가 CANCELED면 취소 저장 후 PAYMENT_CANCELED 예외를 던진다")
    void confirmPayment_fail_whenTossCanceled() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-123";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(new TossPaymentConfirmResult(
                        paymentKey,
                        orderId,
                        TossPaymentStatus.CANCELED,
                        approvedAt
                ));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELED);

        verify(paymentConfirmWriter).saveCanceledAfterConfirmResult(
                paymentId,
                paymentKey,
                "TOSS_CANCELED",
                ErrorCode.PAYMENT_CANCELED.name(),
                "Toss payment status is CANCELED"
        );
        verify(paymentConfirmWriter, never()).saveConfirmedPayment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("저장 단계에서 BusinessException이 발생하면 취소하지 않고 그대로 다시 던진다")
    void confirmPayment_rethrowBusinessException_whenSaveConfirmedPaymentThrowsBusinessException() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        TossPaymentConfirmResult confirmResult = new TossPaymentConfirmResult(
                paymentKey,
                orderId,
                TossPaymentStatus.DONE,
                approvedAt
        );

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));
        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(confirmResult);
        given(paymentConfirmWriter.saveConfirmedPayment(paymentId, paymentKey, confirmResult))
                .willThrow(new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);

        verify(paymentConfirmWriter, never()).saveFailureAfterConfirmSaveError(anyLong(), anyString(), anyString());
        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
        verify(paymentConfirmWriter, never()).saveCancelFailureLog(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("토스 승인 성공 후 저장에 실패하면 결제 취소를 시도하고 예외를 다시 던진다")
    void confirmPayment_cancelWhenSaveFailsAfterConfirm() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        TossPaymentConfirmResult confirmResult = new TossPaymentConfirmResult(
                paymentKey,
                orderId,
                TossPaymentStatus.DONE,
                approvedAt
        );

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));
        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(confirmResult);
        given(paymentConfirmWriter.saveConfirmedPayment(paymentId, paymentKey, confirmResult))
                .willThrow(new RuntimeException("save failed"));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("save failed");

        verify(paymentConfirmWriter).saveFailureAfterConfirmSaveError(
                paymentId,
                paymentKey,
                "save failed"
        );
        verify(tossPaymentClient).cancel(paymentKey, "INTERNAL_ERROR");
        verify(paymentConfirmWriter, never()).saveCancelFailureLog(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("저장 실패 후 결제 취소도 실패하면 원래 저장 실패 예외를 유지한다")
    void confirmPayment_keepOriginalException_whenCancelAlsoFails() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");
        Long paymentId = 100L;
        OffsetDateTime approvedAt = OffsetDateTime.now();

        TossPaymentConfirmResult confirmResult = new TossPaymentConfirmResult(
                paymentKey,
                orderId,
                TossPaymentStatus.DONE,
                approvedAt
        );

        given(paymentConfirmWriter.loadAndValidateReadyPayment(userId, orderId, amount, paymentKey))
                .willReturn(new PaymentLockedInfo(paymentId));
        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(confirmResult);
        given(paymentConfirmWriter.saveConfirmedPayment(paymentId, paymentKey, confirmResult))
                .willThrow(new RuntimeException("save failed"));
        doThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED))
                .when(tossPaymentClient).cancel(paymentKey, "INTERNAL_ERROR");

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("save failed");

        verify(paymentConfirmWriter).saveFailureAfterConfirmSaveError(
                paymentId,
                paymentKey,
                "save failed"
        );
        verify(tossPaymentClient).cancel(paymentKey, "INTERNAL_ERROR");
        verify(paymentConfirmWriter).saveCancelFailureLog(
                paymentId,
                paymentKey,
                ErrorCode.PAYMENT_CANCEL_FAILED.getMessage()
        );
    }
}