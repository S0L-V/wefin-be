package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailVerification;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import com.solv.wefin.domain.auth.repository.EmailVerificationRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final long EXPIRE_MINUTES = 5L;

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESEND = 5;
    private static final long LOCK_MINUTES = 10L;
    private static final long RESEND_COOLDOWN_SECONDS = 60L;
    private static final long RESEND_WINDOW_SECONDS = 600L;

    private final EmailVerificationRepository emailVerificationRepository;
    private final MailService mailService;

    @Transactional
    public void sendVerificationCode(String email, VerificationPurpose purpose) {
        try {
            String normalizedEmail = normalizeEmail(email);
            String code = generateCode();
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime expiresAt = now.plusMinutes(EXPIRE_MINUTES);

            EmailVerification verification = emailVerificationRepository
                    .findByEmailAndPurpose(normalizedEmail, purpose)
                    .orElse(null);

            if (verification != null) {

                if (verification.isLocked(now)) {
                    throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
                }

                if (verification.isResendTooSoon(now, RESEND_COOLDOWN_SECONDS)) {
                    throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOO_FAST_REQUEST);
                }

                if (verification.isResendWindowExpired(now, RESEND_WINDOW_SECONDS)) {
                    verification.resetResendWindow(now);
                } else if (verification.getResendCount() >= MAX_RESEND) {
                    throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOO_MANY_REQUESTS);
                }

                verification.renew(code, expiresAt);
            } else {
                verification = EmailVerification.builder()
                        .email(normalizedEmail)
                        .purpose(purpose)
                        .verificationCode(code)
                        .expiresAt(expiresAt)
                        .build();

                verification.resetResendWindow(now);
            }

            verification.recordResend(now);
            emailVerificationRepository.saveAndFlush(verification);
            mailService.sendVerificationCode(normalizedEmail, code);

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CONCURRENT_REQUEST);
        }
    }

    @Transactional
    public void confirmVerificationCode(String email, String code, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = normalizeCode(code);

        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(normalizedEmail, purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID));

        OffsetDateTime now = OffsetDateTime.now();

        if (verification.isLocked(now)) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
        }

        if (verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
        }

        if (!verification.matchesCode(normalizedCode)) {
            verification.increaseAttempt();

            if (verification.getAttemptCount() >= MAX_ATTEMPTS) {
                verification.lock(now.plusMinutes(LOCK_MINUTES));
            }

            emailVerificationRepository.saveAndFlush(verification);

            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID);
        }

        verification.resetAttempt();
        verification.verify();
        emailVerificationRepository.saveAndFlush(verification);
    }

    public void validateVerifiedEmail(String email, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);

        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(normalizedEmail, purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));

        if (!verification.isVerified()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        if (verification.isExpired(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
        }
    }

    @Transactional
    public void consumeVerifiedEmail(String email, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);

        Optional<EmailVerification> optionalVerification =
                emailVerificationRepository.findByEmailAndPurpose(normalizedEmail, purpose);

        optionalVerification.ifPresent(EmailVerification::consume);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        return normalized;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        String normalized = code.trim();

        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        return normalized;
    }

    private String generateCode() {
        int value = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(value);
    }
}