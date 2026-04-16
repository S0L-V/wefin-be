package com.solv.wefin.domain.auth.service;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(JavaMailSender.class)
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("[wefin] 이메일 인증코드");
            message.setText("인증코드는 [" + code + "] 입니다. 5분 내에 입력해주세요.");

            mailSender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패", e);
            throw new BusinessException(ErrorCode.AUTH_EMAIL_SEND_FAILED);
        }
    }
}