package com.solv.wefin.domain.chat.groupChat.dto.info;

public record ReplyMessageInfo(
        Long messageId,
        String sender,
        String content
) {
}
