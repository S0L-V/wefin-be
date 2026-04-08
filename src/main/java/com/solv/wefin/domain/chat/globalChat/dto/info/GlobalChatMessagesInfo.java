package com.solv.wefin.domain.chat.globalChat.dto.info;

import java.util.List;

public record GlobalChatMessagesInfo(
        List<GlobalChatMessageInfo> messages,
        Long nextCursor,
        boolean hasNext
) {
}
