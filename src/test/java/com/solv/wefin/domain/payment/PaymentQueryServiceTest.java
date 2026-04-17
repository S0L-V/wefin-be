package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.payment.dto.MySubscriptionInfo;
import com.solv.wefin.domain.payment.entity.BillingCycle;
import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionPlanRepository;
import com.solv.wefin.domain.payment.repository.SubscriptionRepository;
import com.solv.wefin.domain.payment.service.PaymentConfirmWriter;
import com.solv.wefin.domain.payment.service.PaymentFailureLogWriter;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

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
    private Subscription subscription;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        subscription = mock(Subscription.class);
        plan = mock(SubscriptionPlan.class);
    }

    @Test
    @DisplayName("내 활성 구독 조회에 성공한다")
    void getMySubscription_success() {
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-04-17T10:00:00+09:00");
        OffsetDateTime expiredAt = OffsetDateTime.parse("2026-05-17T10:00:00+09:00");

        given(subscriptionRepository.findByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));

        given(subscription.getPlan()).willReturn(plan);
        given(subscription.getStatus()).willReturn(SubscriptionStatus.ACTIVE);
        given(subscription.isActive()).willReturn(true);
        given(subscription.getStartedAt()).willReturn(startedAt);
        given(subscription.getExpiredAt()).willReturn(expiredAt);

        given(plan.getPlanId()).willReturn(1L);
        given(plan.getPlanName()).willReturn("프로 플랜");
        given(plan.getPrice()).willReturn(new BigDecimal("9900"));
        given(plan.getBillingCycle()).willReturn(BillingCycle.MONTHLY);
        given(plan.getDescription()).willReturn("무제한 AI 기능과 고급 분석 도구를 제공합니다.");

        MySubscriptionInfo result = paymentService.getMySubscription(userId);

        assertThat(result.planId()).isEqualTo(1L);
        assertThat(result.planName()).isEqualTo("프로 플랜");
        assertThat(result.price()).isEqualByComparingTo("9900");
        assertThat(result.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(result.description()).isEqualTo("무제한 AI 기능과 고급 분석 도구를 제공합니다.");
        assertThat(result.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.active()).isTrue();
        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.expiredAt()).isEqualTo(expiredAt);

        verify(subscriptionRepository).findByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        verifyNoInteractions(
                paymentRepository,
                subscriptionPlanRepository,
                userRepository,
                paymentWriter,
                paymentConfirmWriter,
                tossPaymentClient,
                paymentFailureLogWriter
        );
    }

    @Test
    @DisplayName("활성 구독이 없으면 ACTIVE_SUBSCRIPTION_NOT_FOUND 예외가 발생한다")
    void getMySubscription_fail_whenActiveSubscriptionNotFound() {
        given(subscriptionRepository.findByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getMySubscription(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_SUBSCRIPTION_NOT_FOUND);

        verify(subscriptionRepository).findByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        verifyNoInteractions(
                paymentRepository,
                subscriptionPlanRepository,
                userRepository,
                paymentWriter,
                paymentConfirmWriter,
                tossPaymentClient,
                paymentFailureLogWriter
        );
    }
}