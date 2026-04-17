package com.solv.wefin.domain.auth.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "email_send_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSendLog extends BaseEntity {

    private static final int VISIBLE_CODE_LENGTH = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationPurpose purpose;

    @Column(nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailSendStatus status;

    @Column(nullable = false)
    private int retryCount;

    private OffsetDateTime lastTriedAt;

    @Builder
    public EmailSendLog(String email, VerificationPurpose purpose, String code) {
        this.email = email;
        this.purpose = purpose;
        this.code = maskCode(code);
        this.status = EmailSendStatus.PENDING;
        this.retryCount = 0;
    }

    public void markSuccess(OffsetDateTime now) {
        this.status = EmailSendStatus.SUCCESS;
        this.lastTriedAt = now;
    }

    public void markFail(OffsetDateTime now) {
        this.status = EmailSendStatus.FAIL;
        this.retryCount++;
        this.lastTriedAt = now;
    }

    private static String maskCode(String code) {
        if (code == null || code.length() <= VISIBLE_CODE_LENGTH) {
            return "****";
        }

        String visiblePart = code.substring(code.length() - VISIBLE_CODE_LENGTH);
        return "****" + visiblePart;
    }
}