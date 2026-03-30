package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalChatService {

    private final ApplicationEventPublisher eventPublisher;
    private final GlobalChatMessageRepository globalChatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public void sendMessage(GlobalChatSendRequest request, UUID userId) {

        validateMessage(request.getContent());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.createUserMessage(user, request.getContent())
        );

        eventPublisher.publishEvent(toEvent(savedMessage));
    }

    @Transactional
    public void sendSystemMessage(String content) {

        validateMessage(content);

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.createSystemMessage(content)
        );

        eventPublisher.publishEvent(toEvent(savedMessage));
    }

    private GlobalChatMessageResponse toResponse(GlobalChatMessage message) {
        User user = message.getUser();

        String sender = (message.getRole() == ChatRole.SYSTEM || user == null)
                ? "시스템"
                : user.getNickname();

        UUID userId = user != null ? user.getUserId() : null;

        return GlobalChatMessageResponse.builder()
                .messageId(message.getId())
                .userId(userId)
                .role(message.getRole().name())
                .sender(sender)
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private GlobalChatMessageCreatedEvent toEvent(GlobalChatMessage message) {
        User user = message.getUser();

        String sender = (message.getRole() == ChatRole.SYSTEM || user == null)
                ? "시스템"
                : user.getNickname();

        return new GlobalChatMessageCreatedEvent(
                message.getId(),
                user != null ? user.getUserId() : null,
                message.getRole().name(),
                sender,
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (content.length() > 1000) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    public List<GlobalChatMessageResponse> getRecentMessages(int limit) {

        int size = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, size);

        List<GlobalChatMessage> messages = globalChatMessageRepository.findRecentMessages(pageable);

        return messages.stream()
                .sorted(Comparator.comparing(GlobalChatMessage::getId))
                .map(this::toResponse)
                .toList();
    }
}
