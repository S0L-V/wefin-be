package com.solv.wefin.domain.auth.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "email_verification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_verification_email_purpose",
                        columnNames = {"email", "purpose"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_id")
    private Long id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "verification_code", nullable = false, length = 20)
    private String verificationCode;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_sent_at")
    private OffsetDateTime lastSentAt;

    @Builder
    public EmailVerification(String email,
                             VerificationPurpose purpose,
                             String verificationCode,
                             OffsetDateTime expiresAt) {
        this.email = email;
        this.purpose = purpose;
        this.verificationCode = verificationCode;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.attemptCount = 0;
        this.resendCount = 0;
        this.lockedUntil = null;
        this.lastSentAt = null;
    }

    public void renew(String verificationCode, OffsetDateTime expiresAt) {
        this.verificationCode = verificationCode;
        this.expiresAt = expiresAt;
        this.verified = false;

        this.attemptCount = 0;
        this.lockedUntil = null;
        this.lastSentAt = null;
    }

    public void verify() {
        this.verified = true;
    }

    public void consume() {
        this.verified = false;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean matchesCode(String code) {
        return MessageDigest.isEqual(
                this.verificationCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public void resetAttempt() {
        this.attemptCount = 0;
    }

    public void lock(OffsetDateTime until) {
        this.lockedUntil = until;
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void increaseResend() {
        this.resendCount++;
    }

    public boolean isResendTooSoon(OffsetDateTime now, long cooldownSeconds) {
        return lastSentAt != null && lastSentAt.plusSeconds(cooldownSeconds).isAfter(now);
    }

    public void updateLastSentAt(OffsetDateTime now) {
        this.lastSentAt = now;
    }
}