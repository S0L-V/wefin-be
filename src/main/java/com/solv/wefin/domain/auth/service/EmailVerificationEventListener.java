package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailSendLog;
import com.solv.wefin.domain.auth.repository.EmailSendLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventListener {

    private final MailService mailService;
    private final EmailSendLogRepository emailSendLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSendEmail(EmailVerificationSendEvent event) {
        EmailSendLog emailSendLog = EmailSendLog.builder()
                .email(event.email())
                .purpose(event.purpose())
                .code(event.code())
                .build();

        try {
            mailService.sendVerificationCode(event.email(), event.code());
            emailSendLog.markSuccess(OffsetDateTime.now());
        } catch (Exception e) {
            emailSendLog.markFail(OffsetDateTime.now());
            log.error(
                    "메일 발송 실패 (after commit): email={}, purpose={}, retryCount={}",
                    maskEmail(event.email()),
                    event.purpose(),
                    emailSendLog.getRetryCount(),
                    e
            );
        }

        emailSendLogRepository.save(emailSendLog);
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