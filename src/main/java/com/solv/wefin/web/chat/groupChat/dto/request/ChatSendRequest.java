package com.solv.wefin.web.chat.groupChat.dto.request;

public record ChatSendRequest(
        String content,
        Long replyToMessageId
) {
}
