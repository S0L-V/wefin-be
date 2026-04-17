package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailVerificationEventListenerTest {

    private final MailService mailService = mock(MailService.class);
    private final EmailVerificationEventListener listener =
            new EmailVerificationEventListener(mailService);

    @Test
    @DisplayName("이벤트 수신 시 인증코드 메일을 발송한다")
    void handleSendEmail_success() {
        // given
        EmailVerificationSendEvent event =
                new EmailVerificationSendEvent(
                        "test@example.com",
                        "123456",
                        VerificationPurpose.SIGNUP
                );

        // when
        listener.handleSendEmail(event);

        // then
        verify(mailService).sendVerificationCode("test@example.com", "123456");
    }

    @Test
    @DisplayName("메일 발송 중 예외가 발생해도 예외를 전파하지 않는다")
    void handleSendEmail_ignore_exception() {
        // given
        EmailVerificationSendEvent event =
                new EmailVerificationSendEvent(
                        "test@example.com",
                        "123456",
                        VerificationPurpose.SIGNUP
                );

        doThrow(new RuntimeException("mail send fail"))
                .when(mailService)
                .sendVerificationCode("test@example.com", "123456");

        // when
        listener.handleSendEmail(event);

        // then
        verify(mailService).sendVerificationCode("test@example.com", "123456");
    }
}