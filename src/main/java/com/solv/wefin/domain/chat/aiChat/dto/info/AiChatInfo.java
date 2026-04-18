package com.solv.wefin.domain.chat.aiChat.dto.info;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiChatInfo(
        Long messageId,
        UUID userId,
        String role,
        String content,
        OffsetDateTime createdAt
) {
}
