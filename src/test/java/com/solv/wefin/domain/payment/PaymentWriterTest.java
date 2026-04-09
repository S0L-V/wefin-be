package com.solv.wefin.domain.payment;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.PaymentStatus;
import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import com.solv.wefin.domain.payment.repository.PaymentRepository;
import com.solv.wefin.domain.payment.service.OrderIdGenerator;
import com.solv.wefin.domain.payment.service.PaymentWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWriterTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderIdGenerator orderIdGenerator;

    @Mock
    private SubscriptionPlan plan;

    @Mock
    private User user;

    @InjectMocks
    private PaymentWriter paymentWriter;

    @Test
    @DisplayName("READY 결제를 생성해서 saveAndFlush 한다")
    void saveReadyPayment_success() {
        given(orderIdGenerator.generate()).willReturn("ORDER-20260409-TEST123");
        given(plan.getPrice()).willReturn(new BigDecimal("9900"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        given(paymentRepository.saveAndFlush(paymentCaptor.capture()))
                .willAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentWriter.saveReadyPayment(plan, user, PaymentProvider.TOSS);

        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getOrderId()).isEqualTo("ORDER-20260409-TEST123");
        assertThat(savedPayment.getPlan()).isEqualTo(plan);
        assertThat(savedPayment.getUser()).isEqualTo(user);
        assertThat(savedPayment.getProvider()).isEqualTo(PaymentProvider.TOSS);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo("9900");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.READY);

        assertThat(result).isSameAs(savedPayment);

        verify(orderIdGenerator).generate();
        verify(paymentRepository).saveAndFlush(savedPayment);
    }
}