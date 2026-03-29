package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.entity.Users;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.domain.chat.globalChat.repository.UsersRepository;
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

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GlobalChatService {

    private final ApplicationEventPublisher eventPublisher;
    private final GlobalChatMessageRepository globalChatMessageRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public void sendMessage(GlobalChatSendRequest request, UUID userId) {

        validateMessage(request.getContent());

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.builder()
                        .user(user)
                        .role(ChatRole.USER)
                        .content(request.getContent())
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        eventPublisher.publishEvent(new GlobalChatMessageCreatedEvent(toResponse(savedMessage)));
    }

    @Transactional
    public void sendSystemMessage(String content) {

        validateMessage(content);

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.builder()
                        .user(null)
                        .role(ChatRole.SYSTEM)
                        .content(content)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        eventPublisher.publishEvent(new GlobalChatMessageCreatedEvent(toResponse(savedMessage)));
    }

    private GlobalChatMessageResponse toResponse(GlobalChatMessage message) {
        String sender = message.getRole() == ChatRole.SYSTEM
                ? "시스템"
                : message.getUser().getNickname();

        UUID userId = message.getUser() != null ? message.getUser().getId() : null;

        return GlobalChatMessageResponse.builder()
                .messageId(message.getId())
                .userId(userId)
                .role(message.getRole().name())
                .sender(sender)
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (content.length() > 1000) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    @Transactional(readOnly = true)
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
