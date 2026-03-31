package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.globalChat.dto.GlobalChatMessageInfo;
import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final ChatSpamGuard chatSpamGuard;

    @Transactional
    public void sendMessage(String content, UUID userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        validateMessage(content);

        OffsetDateTime now = OffsetDateTime.now();
        String blockKey = ChatScope.GLOBAL + ":" + userId;

        long recentCount = globalChatMessageRepository.countByUser_UserIdAndCreatedAtAfter(
                userId,
                now.minusSeconds(3)
        );

        // 도배 체크
        chatSpamGuard.validate(blockKey, recentCount, now);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.createUserMessage(user, content)
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

    private GlobalChatMessageInfo toInfo(GlobalChatMessage message) {
        User user = message.getUser();

        String sender = (message.getRole() == ChatRole.SYSTEM || user == null)
                ? "시스템"
                : user.getNickname();

        UUID userId = user != null ? user.getUserId() : null;

        return new GlobalChatMessageInfo(
                message.getId(),
                userId,
                message.getRole().name(),
                sender,
                message.getContent(),
                message.getCreatedAt()
        );
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

    public List<GlobalChatMessageInfo> getRecentMessages(int limit) {

        int size = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, size);

        List<GlobalChatMessage> messages = globalChatMessageRepository.findRecentMessages(pageable);

        return messages.stream()
                .sorted(Comparator.comparing(GlobalChatMessage::getId))
                .map(this::toInfo)
                .toList();
    }
}
