package com.solv.wefin.web.chat.globalChat.dto.response;

import java.util.List;

public record GlobalChatMessagesResponse(
        List<GlobalChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
}
