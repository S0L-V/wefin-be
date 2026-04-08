package com.solv.wefin.web.chat.globalChat.dto.response;

import com.solv.wefin.domain.chat.globalChat.dto.info.GlobalChatMessagesInfo;

import java.util.List;

public record GlobalChatMessagesResponse(
        List<GlobalChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
    public static GlobalChatMessagesResponse from(GlobalChatMessagesInfo info) {
        return new GlobalChatMessagesResponse(
                info.messages().stream()
                        .map(GlobalChatMessageResponse::from)
                        .toList(),
                info.nextCursor(),
                info.hasNext()
        );
    }
}
