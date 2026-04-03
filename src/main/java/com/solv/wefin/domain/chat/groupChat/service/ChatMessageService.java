package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.MessageType;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatSpamGuard chatSpamGuard;

    private static final long SPAM_WINDOW_SECONDS = 3L;
    private static final String SYSTEM = "시스템";

    private final Map<String, Object> chatLocks = new ConcurrentHashMap<>();

    @Transactional
    public void sendMessage(String content, UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        validateMessage(content);

        Group group = findActiveUserGroup(userId);
        Long groupId = group.getId();

        String blockKey = ChatScope.groupKey(groupId, userId);
        Object lock = chatLocks.computeIfAbsent(blockKey, key -> new Object());

        synchronized (lock) {
            OffsetDateTime now = OffsetDateTime.now();

            long recentCount = chatMessageRepository.countByGroup_IdAndUser_UserIdAndCreatedAtAfter(
                    groupId,
                    userId,
                    now.minusSeconds(SPAM_WINDOW_SECONDS)
            );

            chatSpamGuard.validate(blockKey, recentCount, now);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            ChatMessage savedMessage = chatMessageRepository.save(
                    ChatMessage.createUserMessage(user, group, content)
            );

            eventPublisher.publishEvent(toEvent(savedMessage));
        }
    }


    public List<ChatMessageInfo> getRecentMessages(UUID userId, int limit) {

        Group group = findActiveUserGroup(userId);
        Long groupId = group.getId();

        int size = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, size);

        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesByGroupId(groupId, pageable);

        return messages.stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(this::toInfo)
                .toList();
    }

    private ChatMessageInfo toInfo(ChatMessage message) {
        User user = message.getUser();

        Group group = message.getGroup();

        String sender = (message.getMessageType() == MessageType.SYSTEM || user == null)
                ? SYSTEM
                : user.getNickname();

        UUID userId = user != null ? user.getUserId() : null;
        Long groupId = group != null ? group.getId() : null;

        return new ChatMessageInfo(
                message.getId(),
                userId,
                groupId,
                message.getMessageType().name(),
                sender,
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private ChatMessageCreatedEvent toEvent(ChatMessage message) {
        User user = message.getUser();

        Group group = message.getGroup();

        String sender = (message.getMessageType() == MessageType.SYSTEM || user == null)
                ? SYSTEM
                : user.getNickname();

        return new ChatMessageCreatedEvent(
                message.getId(),
                user != null ? user.getUserId() : null,
                group != null ? group.getId() : null,
                message.getMessageType().name(),
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

    private Group findActiveUserGroup(UUID userId) {
        GroupMember groupMember = groupMemberRepository
                .findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN));

        return groupMember.getGroup();
    }

    public Group getMyGroup(UUID userId) {
        return findActiveUserGroup(userId);
    }
}
