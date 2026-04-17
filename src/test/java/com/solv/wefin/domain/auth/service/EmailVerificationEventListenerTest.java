package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailSendLog;
import com.solv.wefin.domain.auth.entity.EmailSendStatus;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import com.solv.wefin.domain.auth.repository.EmailSendLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailVerificationEventListenerTest {

    private final MailService mailService = mock(MailService.class);
    private final EmailSendLogRepository emailSendLogRepository = mock(EmailSendLogRepository.class);

    private final EmailVerificationEventListener listener =
            new EmailVerificationEventListener(mailService, emailSendLogRepository);

    @Test
    @DisplayName("이벤트 수신 시 인증코드 메일을 발송하고 성공 로그를 저장한다")
    void handleSendEmail_success() {
        // given
        EmailVerificationSendEvent event =
                new EmailVerificationSendEvent(
                        "test@example.com",
                        "123456",
                        VerificationPurpose.SIGNUP
                );

        ArgumentCaptor<EmailSendLog> captor =
                ArgumentCaptor.forClass(EmailSendLog.class);

        // when
        listener.handleSendEmail(event);

        // then
        verify(mailService).sendVerificationCode("test@example.com", "123456");
        verify(emailSendLogRepository).save(captor.capture());

        EmailSendLog savedLog = captor.getValue();
        assertThat(savedLog.getEmail()).isEqualTo("test@example.com");
        assertThat(savedLog.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
        assertThat(savedLog.getCode()).isEqualTo("****56");
        assertThat(savedLog.getStatus()).isEqualTo(EmailSendStatus.SUCCESS);
        assertThat(savedLog.getRetryCount()).isEqualTo(0);
        assertThat(savedLog.getLastTriedAt()).isNotNull();
    }

    @Test
    @DisplayName("메일 발송 중 예외가 발생해도 예외를 전파하지 않고 실패 로그를 저장한다")
    void handleSendEmail_ignore_exception() {
        // given
        EmailVerificationSendEvent event =
                new EmailVerificationSendEvent(
                        "test@example.com",
                        "123456",
                        VerificationPurpose.SIGNUP
                );

        ArgumentCaptor<EmailSendLog> captor =
                ArgumentCaptor.forClass(EmailSendLog.class);

        doThrow(new RuntimeException("mail send fail"))
                .when(mailService)
                .sendVerificationCode("test@example.com", "123456");

        // when
        listener.handleSendEmail(event);

        // then
        verify(mailService).sendVerificationCode("test@example.com", "123456");
        verify(emailSendLogRepository).save(captor.capture());

        EmailSendLog savedLog = captor.getValue();
        assertThat(savedLog.getEmail()).isEqualTo("test@example.com");
        assertThat(savedLog.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
        assertThat(savedLog.getCode()).isEqualTo("****56");
        assertThat(savedLog.getStatus()).isEqualTo(EmailSendStatus.FAIL);
        assertThat(savedLog.getRetryCount()).isEqualTo(1);
        assertThat(savedLog.getLastTriedAt()).isNotNull();
    }
}