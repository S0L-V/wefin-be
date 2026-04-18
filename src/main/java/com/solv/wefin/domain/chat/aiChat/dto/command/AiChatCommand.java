package com.solv.wefin.domain.chat.aiChat.dto.command;

public record AiChatCommand(
        String message,
        Long newsClusterId
) {
}
