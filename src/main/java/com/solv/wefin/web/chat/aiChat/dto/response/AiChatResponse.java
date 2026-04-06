package com.solv.wefin.web.chat.aiChat.dto.response;

import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;

public record AiChatResponse(
        String answer
) {
    public static AiChatResponse from(AiChatInfo info) {
        return new AiChatResponse(info.answer());
    }
}
