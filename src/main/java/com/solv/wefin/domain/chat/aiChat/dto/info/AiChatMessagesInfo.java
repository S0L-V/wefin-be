package com.solv.wefin.domain.chat.aiChat.dto.info;

import java.util.List;

public record AiChatMessagesInfo(
        List<AiChatInfo> messages,
        Long nextCursor,
        boolean hasNext
) {
}
