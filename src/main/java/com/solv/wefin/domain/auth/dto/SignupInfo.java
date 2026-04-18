package com.solv.wefin.domain.auth.dto;

import java.util.UUID;

public record SignupInfo(
        UUID userId,
        String email,
        String nickname
) {
}