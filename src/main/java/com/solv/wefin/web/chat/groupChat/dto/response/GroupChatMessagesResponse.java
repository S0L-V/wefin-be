package com.solv.wefin.web.chat.groupChat.dto.response;

import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;

import java.util.List;

public record GroupChatMessagesResponse(
        List<ChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
    public static GroupChatMessagesResponse from(ChatMessagesInfo info) {
        return new GroupChatMessagesResponse(
                info.messages().stream()
                        .map(ChatMessageResponse::from)
                        .toList(),
                info.nextCursor(),
                info.hasNext()
        );
    }
}
