package com.solv.wefin.domain.auth.service;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("[wefin] 이메일 인증코드 안내");
            message.setText(buildVerificationContent(code));

            mailSender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패: to={}", maskEmail(to), e);
            throw new BusinessException(ErrorCode.AUTH_EMAIL_SEND_FAILED);
        }
    }

    private String buildVerificationContent(String code) {
        return """
                안녕하세요, wefin입니다.

                요청하신 인증코드는 아래와 같습니다.

                인증코드: [%s]

                해당 코드는 5분간 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }

        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];

        if (local.isEmpty()) {
            return "***@" + domain;
        }

        return local.charAt(0) + "***@" + domain;
    }
}