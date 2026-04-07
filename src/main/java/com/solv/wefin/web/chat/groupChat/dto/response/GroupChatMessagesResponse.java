package com.solv.wefin.web.chat.groupChat.dto.response;

import java.util.List;

public record GroupChatMessagesResponse(
        List<ChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
}
