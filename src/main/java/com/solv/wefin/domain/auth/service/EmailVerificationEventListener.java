package com.solv.wefin.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSendEmail(EmailVerificationSendEvent event) {
        try {
            mailService.sendVerificationCode(event.email(), event.code());
        } catch (Exception e) {
            log.error("메일 발송 실패 (after commit): email={}", maskEmail(event.email()), e);
        }
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