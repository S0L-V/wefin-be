package com.solv.wefin.domain.auth.service;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MailServiceImplTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailServiceImpl mailService = new MailServiceImpl(mailSender);

    @Test
    @DisplayName("인증코드 메일을 정상적으로 발송한다")
    void sendVerificationCode_success() {
        // given
        String to = "test@example.com";
        String code = "123456";

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        // when
        mailService.sendVerificationCode(to, code);

        // then
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly(to);
        assertThat(message.getSubject()).isEqualTo("[wefin] 이메일 인증코드");
        assertThat(message.getText()).contains(code);
    }

    @Test
    @DisplayName("메일 발송 실패 시 BusinessException이 발생한다")
    void sendVerificationCode_fail_when_mail_exception() {
        // given
        doThrow(new MailException("fail") {})
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mailService.sendVerificationCode("test@example.com", "123456")
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_EMAIL_SEND_FAILED);
    }
}