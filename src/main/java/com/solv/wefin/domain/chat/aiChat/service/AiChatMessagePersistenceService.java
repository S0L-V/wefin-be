package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.domain.chat.aiChat.repository.AiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiChatMessagePersistenceService {

    private final AiChatMessageRepository aiChatMessageRepository;

    public List<AiChatMessage> getMessages(UUID userId, Long beforeMessageId, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);

        if(beforeMessageId == null) {
            return aiChatMessageRepository.findByUser_UserIdOrderByMessageIdDesc(userId, pageable);
        }

        return aiChatMessageRepository.findByUser_UserIdAndMessageIdLessThanOrderByMessageIdDesc(
                userId,
                beforeMessageId,
                pageable
        );
    }

    public List<AiChatMessage> getRecentHistory(UUID userId) {
        return aiChatMessageRepository.findTop10ByUser_UserIdOrderByMessageIdDesc(userId);
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
