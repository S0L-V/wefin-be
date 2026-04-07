package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final OpenAiChatClient openAiChatClient;
    private final AiChatMessagePersistenceService aiChatMessagePersistenceService;
    private final UserRepository userRepository;

    public List<AiChatInfo> getMessages(UUID userId) {
        validateUserId(userId);

        return aiChatMessagePersistenceService.getMessages(userId)
                .stream()
                .map(this::toInfo)
                .toList();
    }

    public AiChatInfo sendMessage(AiChatCommand command, UUID userId) {
        validateUserId(userId);
        validateCommand(command);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<AiChatMessage> history = aiChatMessagePersistenceService.getRecentHistory(userId);

        String answer = openAiChatClient.ask(history, command.message());

        aiChatMessagePersistenceService.saveUserMessage(user, command.message());
        AiChatMessage aiMessage = aiChatMessagePersistenceService.saveAiMessage(user, answer);

        return toInfo(aiMessage);
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateCommand(AiChatCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String message = command.message();

        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    private AiChatInfo toInfo(AiChatMessage message) {
        return new AiChatInfo(
                message.getMessageId(),
                message.getUser().getUserId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
