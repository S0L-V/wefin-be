package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.CreatePaymentCommand;
import com.solv.wefin.domain.payment.dto.PaymentConfirmInfo;
import com.solv.wefin.domain.payment.dto.PaymentReadyInfo;
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
import com.solv.wefin.domain.payment.service.PaymentService;
import com.solv.wefin.domain.payment.service.PaymentWriter;
import com.solv.wefin.domain.payment.service.TossPaymentClient;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class PaymentServiceTest {

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
    private TossPaymentClient tossPaymentClient;

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

    @Nested
    class CreatePaymentTest {

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
            verifyNoInteractions(subscriptionRepository, userRepository, paymentWriter, paymentRepository);
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
            verifyNoInteractions(subscriptionRepository, userRepository, paymentWriter, paymentRepository);
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
            verifyNoInteractions(userRepository, paymentWriter, tossPaymentClient);
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
            verifyNoInteractions(paymentWriter, tossPaymentClient);
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
            verifyNoInteractions(tossPaymentClient);
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
            verifyNoInteractions(tossPaymentClient);
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
            verifyNoInteractions(tossPaymentClient);
        }
    }

    @Nested
    class ConfirmPaymentTest {

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

            given(subscriptionRepository.save(any(Subscription.class)))
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
            verify(subscriptionRepository).save(any(Subscription.class));
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

            verifyNoInteractions(subscriptionRepository, tossPaymentClient);
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
            verifyNoInteractions(tossPaymentClient);
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

            verifyNoInteractions(tossPaymentClient);
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

            verifyNoInteractions(tossPaymentClient);
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

            verifyNoInteractions(tossPaymentClient);
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
            verify(subscriptionRepository, never()).save(any());
        }
    }
}