package com.solv.wefin.web.chat.aiChat.dto.request;

import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank String message
) {
    public AiChatCommand toCommand() {
        return new AiChatCommand(message);
    }
}
