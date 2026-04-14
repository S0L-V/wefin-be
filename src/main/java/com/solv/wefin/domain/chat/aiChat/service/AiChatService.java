package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatMessagesInfo;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.domain.quest.entity.QuestEventType;
import com.solv.wefin.domain.quest.service.QuestProgressService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final OpenAiChatClient openAiChatClient;
    private final AiChatMessagePersistenceService aiChatMessagePersistenceService;
    private final UserRepository userRepository;
    private final QuestProgressService questProgressService;

    public AiChatMessagesInfo getMessages(UUID userId, Long beforeMessageId, int size) {
        validateUserId(userId);

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        List<AiChatMessage> fetched = aiChatMessagePersistenceService.getMessages(userId, beforeMessageId, pageSize);

        boolean hasNext = fetched.size() > pageSize;
        if (hasNext) {
            fetched = fetched.subList(0, pageSize);
        }

        Long nextCursor = hasNext && !fetched.isEmpty()
                ? fetched.get(fetched.size() - 1).getMessageId()
                :null;

        List<AiChatInfo> messages = fetched.stream()
                .sorted(Comparator.comparing(AiChatMessage::getMessageId))
                .map(this::toInfo)
                .toList();

        return new AiChatMessagesInfo(messages, nextCursor, hasNext);
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

        try {
            questProgressService.handleEvent(userId, QuestEventType.USE_AI_CHAT);
        } catch (RuntimeException e) {
            log.warn("퀘스트 진행도 반영 실패 userId={}", userId, e);
        }

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
