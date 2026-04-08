package com.solv.wefin.web.chat.aiChat.dto.response;

import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatMessagesInfo;

import java.util.List;

public record AiChatMessagesResponse(
        List<AiChatResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
    public static AiChatMessagesResponse from(AiChatMessagesInfo info) {
        return new AiChatMessagesResponse(
                info.messages().stream()
                        .map(AiChatResponse::from)
                        .toList(),
                info.nextCursor(),
                info.hasNext()
        );
    }
}
