package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.dto.command.ShareNewsCommand;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.NewsShareInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ReplyMessageInfo;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessageNewsShare;
import com.solv.wefin.domain.chat.groupChat.entity.MessageType;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
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
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final Map<String, Object> chatLocks = new ConcurrentHashMap<>();
    private final NewsClusterRepository newsClusterRepository;
    private final ChatMessageNewsShareService chatMessageNewsShareService;

    @Transactional
    public void sendMessage(String content, UUID userId, Long replyToMessageId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        validateMessage(content);

        Group group = findActiveUserGroup(userId);
        Long groupId = group.getId();

        ChatMessage replyTarget = findReplyTarget(replyToMessageId, groupId);

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
                    ChatMessage.createUserMessage(user, group, content, replyTarget)
            );

            eventPublisher.publishEvent(toEvent(savedMessage));
        }
    }

    @Transactional
    public ChatMessageInfo shareNews(UUID userId, ShareNewsCommand command) {
        validateShareNewsCommand(command);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Group group = findActiveUserGroup(userId);
        NewsCluster newsCluster = newsClusterRepository.findById(command.newsClusterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_CLUSTER_NOT_FOUND));

        ChatMessage replyToMessage = null;
        if (command.replyToMessageId() != null) {
            replyToMessage = chatMessageRepository.findByIdAndGroup_Id(command.replyToMessageId(), group.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        }

        ChatMessage chatMessage = chatMessageRepository.save(
                ChatMessage.createNewsMessage(user, group, replyToMessage)
        );

        chatMessageNewsShareService.save(chatMessage, newsCluster);

        ChatMessageInfo info = toInfo(chatMessage);

        eventPublisher.publishEvent(new ChatMessageCreatedEvent(group.getId(), info));

        return info;
    }

    public ChatMessagesInfo getMessages(UUID userId, Long beforeMessageId, int size) {

        Group group = findActiveUserGroup(userId);
        Long groupId = group.getId();

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<ChatMessage> fetched = beforeMessageId == null
                ? chatMessageRepository.findMessagesByGroupId(groupId, pageable)
                : chatMessageRepository.findMessagesByGroupIdBefore(groupId, beforeMessageId, pageable);

        boolean hasNext = fetched.size() > pageSize;
        if (hasNext) {
            fetched = fetched.subList(0, pageSize);
        }

        Long nextCursor = hasNext && !fetched.isEmpty()
                ? fetched.get(fetched.size() - 1).getId()
                :null;

        List<ChatMessageInfo> messages = fetched.stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(this::toInfo)
                .toList();

        return new ChatMessagesInfo(messages, nextCursor, hasNext);
    }

    private ChatMessageInfo toInfo(ChatMessage message) {
        NewsShareInfo newsShareInfo = null;

        if (message.getNewsShare() != null) {
            ChatMessageNewsShare newsShare = message.getNewsShare();
            newsShareInfo = new NewsShareInfo(
                    newsShare.getNewsCluster().getId(),
                    newsShare.getSharedTitle(),
                    newsShare.getSharedSummary(),
                    newsShare.getSharedThumbnailUrl()
            );
        }

        return new ChatMessageInfo(
                message.getId(),
                extractUserId(message),
                extractGroupId(message),
                message.getMessageType().name(),
                resolveSender(message),
                message.getContent(),
                message.getCreatedAt(),
                toReplyInfo(message.getReplyToMessage()),
                newsShareInfo
        );
    }

    private ReplyMessageInfo toReplyInfo(ChatMessage replyMessage) {
        if(replyMessage == null) {
            return null;
        }

        String previewContent = replyMessage.getContent();

        if (replyMessage.getMessageType() == MessageType.NEWS && replyMessage.getNewsShare() != null) {
            previewContent = "[뉴스] " + replyMessage.getNewsShare().getSharedTitle();
        }

        return new ReplyMessageInfo(
                replyMessage.getId(),
                resolveSender(replyMessage),
                previewContent
        );
    }

    private ChatMessageCreatedEvent toEvent(ChatMessage message) {
        return new ChatMessageCreatedEvent(
                extractGroupId(message),
                toInfo(message)
        );
    }

    private void validateShareNewsCommand(ShareNewsCommand command) {
        if(command == null || command.newsClusterId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (content.length() > MAX_MESSAGE_LENGTH) {
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

    private String resolveSender(ChatMessage message) {
        User user = message.getUser();

        if (message.getMessageType() == MessageType.SYSTEM || user == null) {
            return SYSTEM;
        }

        return user.getNickname();
    }

    private UUID extractUserId(ChatMessage message) {
        User user = message.getUser();

        if (message.getMessageType() == MessageType.SYSTEM || user == null) {
            return null;
        }

        return user.getUserId();
    }

    private Long extractGroupId(ChatMessage message) {
        Group group = message.getGroup();
        return group != null ? group.getId() : null;
    }

    private ChatMessage findReplyTarget(Long replyToMessageId, Long groupId) {
        if(replyToMessageId == null) {
            return null;
        }

        return chatMessageRepository.findByIdAndGroup_Id(replyToMessageId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

}
