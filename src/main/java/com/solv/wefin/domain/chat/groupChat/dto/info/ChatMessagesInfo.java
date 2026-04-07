package com.solv.wefin.domain.chat.groupChat.dto.info;

import java.util.List;

public record ChatMessagesInfo(
        List<ChatMessageInfo> messages,
        Long nextCursor,
        boolean hasNext
) {
}
