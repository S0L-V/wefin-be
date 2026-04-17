package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.VerificationPurpose;

public record EmailVerificationSendEvent(
        String email,
        String code,
        VerificationPurpose purpose
) {
}