package com.solv.wefin.domain.chat.groupChat.dto.info;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageInfo(
        Long messageId,
        UUID userId,
        Long groupId,
        String messageType,
        String sender,
        String content,
        OffsetDateTime createdAt,
        ReplyMessageInfo replyTo,
        NewsShareInfo newsShare
) {
}
