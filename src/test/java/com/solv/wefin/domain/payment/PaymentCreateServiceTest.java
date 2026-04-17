package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.CreatePaymentCommand;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
import com.solv.wefin.domain.payment.entity.BillingCycle;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.PaymentStatus;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionPlanRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.domain.payment.service.*;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCreateServiceTest {

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

    @Mock
    private PaymentFailureLogWriter paymentFailureLogWriter;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private CreatePaymentCommand command;
    private SubscriptionPlan plan;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        command = new CreatePaymentCommand(1L, "TOSS");
        plan = mock(SubscriptionPlan.class);
        user = mock(User.class);
    }

    @Test
    @DisplayName("존재하지 않는 플랜이면 PLAN_NOT_FOUND 예외가 발생한다")
    void createPayment_fail_whenPlanNotFound() {
        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(userId, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_FOUND);

        verify(subscriptionPlanRepository).findById(command.planId());
        verifyNoInteractions(subscriptionRepository, userRepository, paymentWriter, paymentRepository, tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("비활성 플랜이면 PLAN_INACTIVE 예외가 발생한다")
    void createPayment_fail_whenPlanInactive() {
        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(false);

        assertThatThrownBy(() -> paymentService.createPayment(userId, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_INACTIVE);

        verify(subscriptionPlanRepository).findById(command.planId());
        verify(plan).isAvailable();
        verifyNoInteractions(subscriptionRepository, userRepository, paymentWriter, paymentRepository, tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("이미 활성 구독이 있으면 ACTIVE_SUBSCRIPTION_ALREADY_EXISTS 예외가 발생한다")
    void createPayment_fail_whenActiveSubscriptionExists() {
        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(true);
        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(userId, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_SUBSCRIPTION_ALREADY_EXISTS);

        verify(subscriptionRepository).existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        verifyNoInteractions(userRepository, paymentWriter, tossPaymentClient, paymentConfirmWriter);
        verify(paymentRepository, never())
                .findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(any(), any(), any(), any());
    }

    @Test
    @DisplayName("기존 READY 결제가 있으면 새로 생성하지 않고 기존 결제를 반환한다")
    void createPayment_returnsExistingReadyPayment() {
        Payment existingPayment = mock(Payment.class);
        OffsetDateTime requestedAt = OffsetDateTime.now();

        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(true);
        given(plan.getPlanId()).willReturn(1L);
        given(plan.getPlanName()).willReturn("월간 이용권");
        given(plan.getBillingCycle()).willReturn(BillingCycle.MONTHLY);

        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);
        given(paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY))
                .willReturn(Optional.of(existingPayment));

        given(existingPayment.getPaymentId()).willReturn(10L);
        given(existingPayment.getOrderId()).willReturn("ORDER-20260408-EXIST123");
        given(existingPayment.getPlan()).willReturn(plan);
        given(existingPayment.getAmount()).willReturn(new BigDecimal("9900"));
        given(existingPayment.getProvider()).willReturn(PaymentProvider.TOSS);
        given(existingPayment.getStatus()).willReturn(PaymentStatus.READY);
        given(existingPayment.getRequestedAt()).willReturn(requestedAt);

        PaymentReadyInfo result = paymentService.createPayment(userId, command);

        assertThat(result.paymentId()).isEqualTo(10L);
        assertThat(result.orderId()).isEqualTo("ORDER-20260408-EXIST123");
        assertThat(result.planId()).isEqualTo(1L);
        assertThat(result.planName()).isEqualTo("월간 이용권");
        assertThat(result.billingCycle()).isEqualTo("MONTHLY");
        assertThat(result.amount()).isEqualByComparingTo("9900");
        assertThat(result.provider()).isEqualTo("TOSS");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.requestedAt()).isEqualTo(requestedAt);

        verify(userRepository, never()).findById(any());
        verifyNoInteractions(paymentWriter, tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("정상 요청이면 READY 결제를 생성하고 반환한다")
    void createPayment_success() {
        Payment savedPayment = mock(Payment.class);
        OffsetDateTime requestedAt = OffsetDateTime.now();

        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(true);
        given(plan.getPlanId()).willReturn(1L);
        given(plan.getPlanName()).willReturn("월간 이용권");
        given(plan.getBillingCycle()).willReturn(BillingCycle.MONTHLY);

        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);
        given(paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY))
                .willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(paymentWriter.saveReadyPayment(plan, user, PaymentProvider.TOSS))
                .willReturn(savedPayment);

        given(savedPayment.getPaymentId()).willReturn(11L);
        given(savedPayment.getOrderId()).willReturn("ORDER-20260408-NEW12345");
        given(savedPayment.getPlan()).willReturn(plan);
        given(savedPayment.getAmount()).willReturn(new BigDecimal("9900"));
        given(savedPayment.getProvider()).willReturn(PaymentProvider.TOSS);
        given(savedPayment.getStatus()).willReturn(PaymentStatus.READY);
        given(savedPayment.getRequestedAt()).willReturn(requestedAt);

        PaymentReadyInfo result = paymentService.createPayment(userId, command);

        assertThat(result.paymentId()).isEqualTo(11L);
        assertThat(result.orderId()).isEqualTo("ORDER-20260408-NEW12345");
        assertThat(result.planId()).isEqualTo(1L);
        assertThat(result.planName()).isEqualTo("월간 이용권");
        assertThat(result.billingCycle()).isEqualTo("MONTHLY");
        assertThat(result.amount()).isEqualByComparingTo("9900");
        assertThat(result.provider()).isEqualTo("TOSS");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.requestedAt()).isEqualTo(requestedAt);

        verify(paymentWriter).saveReadyPayment(plan, user, PaymentProvider.TOSS);
        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("결제 저장 시 unique 충돌이 발생하면 재조회 후 기존 READY 결제를 반환한다")
    void createPayment_returnsConcurrentReady_whenDataIntegrityViolationOccurs() {
        Payment concurrentReady = mock(Payment.class);
        OffsetDateTime requestedAt = OffsetDateTime.now();

        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(true);
        given(plan.getPlanId()).willReturn(1L);
        given(plan.getPlanName()).willReturn("월간 이용권");
        given(plan.getBillingCycle()).willReturn(BillingCycle.MONTHLY);

        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(user.getUserId()).willReturn(userId);

        given(paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(concurrentReady));

        given(paymentWriter.saveReadyPayment(plan, user, PaymentProvider.TOSS))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        given(concurrentReady.getPaymentId()).willReturn(12L);
        given(concurrentReady.getOrderId()).willReturn("ORDER-20260408-CONCURRENT");
        given(concurrentReady.getPlan()).willReturn(plan);
        given(concurrentReady.getAmount()).willReturn(new BigDecimal("9900"));
        given(concurrentReady.getProvider()).willReturn(PaymentProvider.TOSS);
        given(concurrentReady.getStatus()).willReturn(PaymentStatus.READY);
        given(concurrentReady.getRequestedAt()).willReturn(requestedAt);

        PaymentReadyInfo result = paymentService.createPayment(userId, command);

        assertThat(result.paymentId()).isEqualTo(12L);
        assertThat(result.orderId()).isEqualTo("ORDER-20260408-CONCURRENT");
        assertThat(result.status()).isEqualTo("READY");

        verify(paymentWriter, times(1)).saveReadyPayment(plan, user, PaymentProvider.TOSS);
        verify(paymentRepository, times(2))
                .findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                        userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY);
        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
    }

    @Test
    @DisplayName("결제 저장 충돌이 반복되고 기존 READY도 없으면 최대 3번 재시도 후 INTERNAL_SERVER_ERROR 예외가 발생한다")
    void createPayment_fail_whenSaveConflictRepeatsWithoutConcurrentReady() {
        given(subscriptionPlanRepository.findById(command.planId()))
                .willReturn(Optional.of(plan));
        given(plan.isAvailable()).willReturn(true);
        given(plan.getPlanId()).willReturn(1L);

        given(subscriptionRepository.existsByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(user.getUserId()).willReturn(userId);

        given(paymentRepository.findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY))
                .willReturn(Optional.empty());

        given(paymentWriter.saveReadyPayment(plan, user, PaymentProvider.TOSS))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> paymentService.createPayment(userId, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        verify(paymentWriter, times(3)).saveReadyPayment(plan, user, PaymentProvider.TOSS);
        verify(paymentRepository, times(4))
                .findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
                        userId, 1L, PaymentProvider.TOSS, PaymentStatus.READY);
        verifyNoInteractions(tossPaymentClient, paymentConfirmWriter);
    }
}