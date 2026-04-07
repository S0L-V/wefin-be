package com.solv.wefin.web.chat.aiChat.dto.response;

import java.util.List;

public record AiChatMessagesResponse(
        List<AiChatResponse> messages,
        Long nextCursor,
        boolean hasNext
) {

}
