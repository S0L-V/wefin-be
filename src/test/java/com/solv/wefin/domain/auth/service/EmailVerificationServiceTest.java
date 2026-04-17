package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailVerification;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import com.solv.wefin.domain.auth.repository.EmailVerificationRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmailVerificationService service;

    private static final VerificationPurpose PURPOSE = VerificationPurpose.SIGNUP;

    private EmailVerification createVerification(String email, String code) {
        return EmailVerification.builder()
                .email(email)
                .purpose(PURPOSE)
                .verificationCode(code)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
    }

    @Test
    @DisplayName("인증코드 5회 틀리면 잠금된다")
    void lock_after_max_attempts() {
        // given
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        // when
        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class,
                    () -> service.confirmVerificationCode(email, "wrong", PURPOSE));
        }

        // then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmVerificationCode(email, "wrong", PURPOSE));

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);

        verify(repository, atLeastOnce()).saveAndFlush(verification);
    }

    @Test
    @DisplayName("잠금 상태에서는 인증이 차단된다")
    void blocked_when_locked() {
        // given
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        verification.lock(OffsetDateTime.now().plusMinutes(10));

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmVerificationCode(email, "123456", PURPOSE));

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
    }

    @Test
    @DisplayName("재발송 쿨타임이 적용된다")
    void resend_cooldown() {
        // given
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");
        OffsetDateTime now = OffsetDateTime.now();

        verification.resetResendWindow(now.minusSeconds(30));
        verification.recordResend(now);

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendVerificationCode(email, PURPOSE)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_FAST_REQUEST);

        verify(eventPublisher, never()).publishEvent(any());
        verify(repository, never()).saveAndFlush(any(EmailVerification.class));
    }

    @Test
    @DisplayName("10분 내 재발송 횟수 초과 시 예외가 발생한다")
    void resend_limit_exceeded_within_window() {
        // given
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");
        OffsetDateTime now = OffsetDateTime.now();

        verification.resetResendWindow(now.minusMinutes(5));
        for (int i = 0; i < 5; i++) {
            verification.recordResend(now.minusSeconds(61 + i));
        }

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendVerificationCode(email, PURPOSE)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_REQUESTS);

        verify(eventPublisher, never()).publishEvent(any());
        verify(repository, never()).saveAndFlush(any(EmailVerification.class));
    }

    @Test
    @DisplayName("재발송 윈도우가 만료되면 다시 요청할 수 있다")
    void resend_allowed_when_window_expired() {
        // given
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");
        OffsetDateTime now = OffsetDateTime.now();

        verification.resetResendWindow(now.minusMinutes(11));
        for (int i = 0; i < 5; i++) {
            verification.recordResend(now.minusMinutes(11).plusSeconds(i));
        }

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));
        when(repository.saveAndFlush(any(EmailVerification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        assertDoesNotThrow(() -> service.sendVerificationCode(email, PURPOSE));

        // then
        verify(repository).saveAndFlush(verification);

        ArgumentCaptor<EmailVerificationSendEvent> captor =
                ArgumentCaptor.forClass(EmailVerificationSendEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        EmailVerificationSendEvent event = captor.getValue();
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.code()).isNotBlank();
        assertThat(event.purpose()).isEqualTo(PURPOSE);
    }
}