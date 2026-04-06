package com.solv.wefin.web.chat.aiChat.dto.request;

import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;

public record AiChatRequest(
        String message
) {
    public AiChatCommand toCommand() {
        return new AiChatCommand(message);
    }
}
