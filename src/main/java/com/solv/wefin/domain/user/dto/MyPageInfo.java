package com.solv.wefin.domain.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MyPageInfo(
        UUID userId,
        String email,
        String nickname,
        OffsetDateTime createdAt
) {
}
