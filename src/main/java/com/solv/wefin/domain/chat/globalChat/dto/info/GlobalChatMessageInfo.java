package com.solv.wefin.domain.chat.globalChat.dto.info;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GlobalChatMessageInfo(
        Long messageId,
        UUID userId,
        String role,
        String sender,
        String content,
        OffsetDateTime createdAt) {
}
