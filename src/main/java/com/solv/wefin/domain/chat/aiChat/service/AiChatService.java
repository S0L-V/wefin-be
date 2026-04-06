package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final OpenAiChatClient openAiChatClient;

    public AiChatInfo sendMessage(AiChatCommand command) {
        validateMessage(command.message());

        String answer = openAiChatClient.ask(command.message());

        return new AiChatInfo(answer);
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
    }
}
