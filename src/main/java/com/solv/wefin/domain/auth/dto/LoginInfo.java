package com.solv.wefin.domain.auth.dto;

import java.util.UUID;

public record LoginInfo(
        UUID userId,
        String nickname,
        String accessToken,
        String refreshToken
) {
}
