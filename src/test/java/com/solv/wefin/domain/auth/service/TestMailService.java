package com.solv.wefin.domain.auth.service;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile("test")
public class TestMailService implements MailService {

    @Override
    public void sendVerificationCode(String to, String code) {
        // 테스트에서는 실제 메일 발송하지 않음
    }
}