package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.domain.chat.aiChat.repository.AiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiChatMessagePersistenceService {

    private final AiChatMessageRepository aiChatMessageRepository;

    public List<AiChatMessage> getMessages(UUID userId) {
        return aiChatMessageRepository.findByUser_UserIdOrderByCreatedAtAsc(userId);
    }

    public List<AiChatMessage> getRecentHistory(UUID userId) {
        return aiChatMessageRepository.findTop10ByUser_UserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public AiChatMessage saveUserMessage(User user, String message) {
        return aiChatMessageRepository.save(
                AiChatMessage.createUserMessage(user, message)
        );
    }

    @Transactional
    public AiChatMessage saveAiMessage(User user, String answer) {
        return aiChatMessageRepository.save(
                AiChatMessage.createAiMessage(user, answer)
        );
    }
}
