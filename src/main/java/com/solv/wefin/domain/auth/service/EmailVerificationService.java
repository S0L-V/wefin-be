package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.EmailVerification;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import com.solv.wefin.domain.auth.repository.EmailVerificationRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    private final EmailVerificationRepository emailVerificationRepository;
    private final MailService mailService;

    // 인증코드 발송
    @Transactional
    public void sendVerificationCode(String email, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        String code = generateCode();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(EXPIRE_MINUTES);

        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(normalizedEmail, purpose)
                .map(saved -> {
                    saved.renew(code, expiresAt);
                    return saved;
                })
                .orElseGet(() -> EmailVerification.builder()
                        .email(normalizedEmail)
                        .purpose(purpose)
                        .verificationCode(code)
                        .expiresAt(expiresAt)
                        .build());

        emailVerificationRepository.save(verification);
        mailService.sendVerificationCode(normalizedEmail, code);
    }

    // 인증코드 확인
    @Transactional
    public void confirmVerificationCode(String email, String code, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = normalizeCode(code);

        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(normalizedEmail, purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID));

        if (verification.isExpired(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
        }

        if (!verification.matchesCode(normalizedCode)) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID);
        }

        verification.verify();
    }

    // 인증 완료 여부 확인 (signup, resetPassword에서 사용)
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

    // 인증 소모 (한 번 쓰고 끝)
    @Transactional
    public void consumeVerifiedEmail(String email, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);

        Optional<EmailVerification> optionalVerification =
                emailVerificationRepository.findByEmailAndPurpose(normalizedEmail, purpose);

        optionalVerification.ifPresent(EmailVerification::consume);
    }

    // ===== 내부 유틸 =====

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