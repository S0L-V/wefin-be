package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.TossPaymentConfirmResult;
import com.solv.wefin.domain.payment.entity.BillingCycle;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.PaymentStatus;
import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("결제 승인 성공 시 결제 상태를 PAID로 변경하고 활성 구독을 생성한다")
    void confirmPayment_success() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        Payment payment = mock(Payment.class);
        SubscriptionPlan confirmPlan = mock(SubscriptionPlan.class);
        User confirmUser = mock(User.class);
        Subscription savedSubscription = mock(Subscription.class);

        OffsetDateTime approvedAt = OffsetDateTime.now();
        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime expiredAt = startedAt.plusMonths(1);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(true);
        given(payment.isReady()).willReturn(true);
        given(payment.getAmount()).willReturn(amount);

        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willReturn(new TossPaymentConfirmResult(paymentKey, orderId, "DONE"));

        given(payment.getPlan()).willReturn(confirmPlan);
        given(payment.getUser()).willReturn(confirmUser);
        given(confirmPlan.getBillingCycle()).willReturn(BillingCycle.MONTHLY);

        given(paymentConfirmWriter.savePaidPaymentAndSubscription(eq(payment), any(Subscription.class)))
                .willReturn(savedSubscription);

        given(payment.getPaymentId()).willReturn(100L);
        given(payment.getOrderId()).willReturn(orderId);
        given(confirmPlan.getPlanId()).willReturn(1L);
        given(confirmPlan.getPlanName()).willReturn("월간 이용권");
        given(payment.getAmount()).willReturn(amount);
        given(payment.getProvider()).willReturn(PaymentProvider.TOSS);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);
        given(payment.getProviderPaymentKey()).willReturn(paymentKey);
        given(payment.getApprovedAt()).willReturn(approvedAt);

        given(savedSubscription.getStartedAt()).willReturn(startedAt);
        given(savedSubscription.getExpiredAt()).willReturn(expiredAt);

        PaymentConfirmInfo result = paymentService.confirmPayment(
                userId,
                paymentKey,
                orderId,
                amount
        );

        assertThat(result.paymentId()).isEqualTo(100L);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.planId()).isEqualTo(1L);
        assertThat(result.planName()).isEqualTo("월간 이용권");
        assertThat(result.billingCycle()).isEqualTo("MONTHLY");
        assertThat(result.amount()).isEqualByComparingTo("9900");
        assertThat(result.provider()).isEqualTo("TOSS");
        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.providerPaymentKey()).isEqualTo(paymentKey);
        assertThat(result.approvedAt()).isEqualTo(approvedAt);
        assertThat(result.subscriptionStartedAt()).isEqualTo(startedAt);
        assertThat(result.subscriptionExpiredAt()).isEqualTo(expiredAt);

        verify(tossPaymentClient).confirm(paymentKey, orderId, amount);
        verify(payment).markPaid(paymentKey);
        verify(paymentConfirmWriter).savePaidPaymentAndSubscription(eq(payment), any(Subscription.class));
    }

    @Test
    @DisplayName("orderId에 해당하는 결제가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void confirmPayment_fail_whenPaymentNotFound() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-NOT-FOUND";
        BigDecimal amount = new BigDecimal("9900");

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verifyNoInteractions(subscriptionRepository, tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("본인 결제가 아니면 PAYMENT_OWNERSHIP_MISMATCH 예외가 발생한다")
    void confirmPayment_fail_whenOwnershipMismatch() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        Payment payment = mock(Payment.class);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(false);

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_OWNERSHIP_MISMATCH);

        verify(subscriptionRepository, never()).existsByUserUserIdAndStatus(any(), any());
        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("READY 상태가 아니면 PAYMENT_NOT_READY 예외가 발생한다")
    void confirmPayment_fail_whenPaymentNotReady() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        Payment payment = mock(Payment.class);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(true);
        given(payment.isReady()).willReturn(false);

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_READY);

        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
        verify(subscriptionRepository, never()).existsByUserUserIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("결제 금액이 다르면 PAYMENT_AMOUNT_MISMATCH 예외가 발생한다")
    void confirmPayment_fail_whenAmountMismatch() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";

        Payment payment = mock(Payment.class);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(true);
        given(payment.isReady()).willReturn(true);
        given(payment.getAmount()).willReturn(new BigDecimal("9900"));

        assertThatThrownBy(() -> paymentService.confirmPayment(
                userId,
                paymentKey,
                orderId,
                new BigDecimal("10000")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
        verify(subscriptionRepository, never()).existsByUserUserIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("이미 활성 구독이 있으면 ACTIVE_SUBSCRIPTION_ALREADY_EXISTS 예외가 발생한다")
    void confirmPayment_fail_whenActiveSubscriptionExists() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        Payment payment = mock(Payment.class);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(true);
        given(payment.isReady()).willReturn(true);
        given(payment.getAmount()).willReturn(amount);
        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(true);

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);

        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
        verify(payment, never()).markPaid(any());
    }

    @Test
    @DisplayName("토스 승인에 실패하면 PAYMENT_CONFIRM_FAILED 예외가 발생한다")
    void confirmPayment_fail_whenTossConfirmFails() {
        String paymentKey = "pay_test_123";
        String orderId = "ORDER-20260414-12345";
        BigDecimal amount = new BigDecimal("9900");

        Payment payment = mock(Payment.class);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));
        given(payment.isOwnedBy(userId)).willReturn(true);
        given(payment.isReady()).willReturn(true);
        given(payment.getAmount()).willReturn(amount);
        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);

        given(tossPaymentClient.confirm(paymentKey, orderId, amount))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED));

        assertThatThrownBy(() -> paymentService.confirmPayment(userId, paymentKey, orderId, amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED);

        verify(tossPaymentClient).confirm(paymentKey, orderId, amount);
        verify(payment, never()).markPaid(any());
        verify(paymentConfirmWriter, never()).savePaidPaymentAndSubscription(any(), any());
    }
}