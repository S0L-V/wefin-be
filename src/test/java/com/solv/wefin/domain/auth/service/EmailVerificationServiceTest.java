package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailVerification;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import com.solv.wefin.domain.auth.repository.EmailVerificationRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository repository;

    @Mock
    private MailService mailService;

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
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class,
                    () -> service.confirmVerificationCode(email, "wrong", PURPOSE));
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmVerificationCode(email, "wrong", PURPOSE));

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
    }

    @Test
    @DisplayName("잠금 상태에서는 인증이 차단된다")
    void blocked_when_locked() {
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        verification.lock(OffsetDateTime.now().plusMinutes(10));

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmVerificationCode(email, "123456", PURPOSE));

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
    }

    @Test
    @DisplayName("재발송 쿨타임이 적용된다")
    void resend_cooldown() {
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        verification.increaseResend();
        verification.updateLastSentAt(OffsetDateTime.now());

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendVerificationCode(email, PURPOSE)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_FAST_REQUEST);

        verify(mailService, never()).sendVerificationCode(anyString(), anyString());
        verify(repository, never()).save(any(EmailVerification.class));
    }

    @Test
    @DisplayName("재발송 횟수 초과 시 예외가 발생한다")
    void resend_limit_exceeded() {
        String email = "test@example.com";
        EmailVerification verification = createVerification(email, "123456");

        for (int i = 0; i < 5; i++) {
            verification.increaseResend();
        }

        when(repository.findByEmailAndPurpose(email, PURPOSE))
                .thenReturn(Optional.of(verification));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendVerificationCode(email, PURPOSE));

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_VERIFICATION_TOO_MANY_REQUESTS);

        verify(mailService, never()).sendVerificationCode(anyString(), anyString());
        verify(repository, never()).save(any(EmailVerification.class));
    }
}