package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.dto.command.ShareNewsCommand;
import com.solv.wefin.domain.chat.groupChat.dto.info.*;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessageNewsShare;
import com.solv.wefin.domain.chat.groupChat.entity.MessageType;
import com.solv.wefin.domain.chat.groupChat.entity.RefType;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.quest.entity.QuestEventType;
import com.solv.wefin.domain.quest.service.QuestProgressService;
import com.solv.wefin.domain.vote.entity.Vote;
import com.solv.wefin.domain.vote.entity.VoteOption;
import com.solv.wefin.domain.vote.entity.VoteStatus;
import com.solv.wefin.domain.vote.repository.VoteOptionRepository;
import com.solv.wefin.domain.vote.repository.VoteRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatSpamGuard chatSpamGuard;
    private final QuestProgressService questProgressService;
    private final VoteRepository voteRepository;
    private final VoteOptionRepository voteOptionRepository;

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

        questProgressService.handleEvent(userId, QuestEventType.SEND_GROUP_CHAT);
    }

    @Transactional
    public ChatMessageInfo shareNews(UUID userId, ShareNewsCommand command) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        validateShareNewsCommand(command);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Group group = findActiveUserGroup(userId);
        NewsCluster newsCluster = newsClusterRepository.findById(command.newsClusterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_CLUSTER_NOT_FOUND));

        ChatMessage chatMessage = chatMessageRepository.save(
                ChatMessage.createNewsMessage(user, group, newsCluster.getTitle())
        );

        chatMessageNewsShareService.save(chatMessage, newsCluster);

        ChatMessageInfo info = toInfo(chatMessage);

        eventPublisher.publishEvent(new ChatMessageCreatedEvent(group.getId(), info));

        handleQuestEventSafely(userId, QuestEventType.SHARE_NEWS);
        handleQuestEventSafely(userId, QuestEventType.SEND_GROUP_CHAT);

        return info;
    }

    @Transactional
    public ChatMessageInfo shareVote(UUID userId, Vote vote) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (vote == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean isActiveMember = groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                userId,
                vote.getGroup(),
                GroupMember.GroupMemberStatus.ACTIVE
        );

        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN);
        }

        ChatMessage chatMessage = chatMessageRepository.save(
                ChatMessage.createVoteMessage(
                        user,
                        vote.getGroup(),
                        vote.getTitle(),
                        vote.getVoteId()
                )
        );

        ChatMessageInfo info = toInfo(chatMessage);

        eventPublisher.publishEvent(new ChatMessageCreatedEvent(vote.getGroup().getId(), info));

        handleQuestEventSafely(userId, QuestEventType.SEND_GROUP_CHAT);

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

        List<Long> voteIds = fetched.stream()
                .filter(message -> message.getRefType() == RefType.VOTE && message.getRefId() != null)
                .map(ChatMessage::getRefId)
                .distinct()
                .toList();

        Map<Long, Vote> voteMap = voteIds.isEmpty()
                ? Map.of()
                : voteRepository.findAllByVoteIdIn(voteIds).stream()
                        .collect(Collectors.toMap(Vote::getVoteId, Function.identity()));

        Map<Long, List<VoteOption>> voteOptionMap = voteIds.isEmpty()
                ? Map.of()
                : voteOptionRepository.findAllByVote_VoteIdInOrderByVote_VoteIdAscIdAsc(voteIds).stream()
                        .collect(Collectors.groupingBy(option -> option.getVote().getVoteId()));

        List<ChatMessageInfo> messages = fetched.stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(message -> toInfo(message, voteMap, voteOptionMap))
                .toList();

        return new ChatMessagesInfo(messages, nextCursor, hasNext);
    }

    private ChatMessageInfo toInfo(ChatMessage message) {
        return toInfo(message, Map.of(), Map.of());
    }

    private ChatMessageInfo toInfo(
            ChatMessage message,
            Map<Long, Vote> voteMap,
            Map<Long, List<VoteOption>> voteOptionMap
    ) {
        NewsShareInfo newsShareInfo = null;
        VoteShareInfo voteShareInfo = null;

        if (message.getRefType() == RefType.VOTE && message.getRefId() != null) {
            Vote vote = voteMap.isEmpty()
                    ? voteRepository.findById(message.getRefId()).orElse(null)
                    : voteMap.get(message.getRefId());

            if (vote != null) {
                List<VoteShareOptionInfo> options = (voteOptionMap.isEmpty()
                        ? voteOptionRepository.findAllByVote_VoteIdOrderByIdAsc(vote.getVoteId())
                        : voteOptionMap.getOrDefault(vote.getVoteId(), List.of()))
                        .stream()
                        .map(option -> new VoteShareOptionInfo(option.getId(), option.getOptionText()))
                        .toList();

                boolean closed = vote.getStatus() == VoteStatus.CLOSED
                        || (vote.getEndsAt() != null && OffsetDateTime.now().isAfter(vote.getEndsAt()));

                voteShareInfo = new VoteShareInfo(
                        vote.getVoteId(),
                        vote.getTitle(),
                        vote.getStatus().name(),
                        vote.getMaxSelectCount(),
                        vote.getEndsAt(),
                        closed,
                        options
                );
            }
        }

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
                newsShareInfo,
                voteShareInfo
        );
    }

    private ReplyMessageInfo toReplyInfo(ChatMessage replyMessage) {
        if(replyMessage == null) {
            return null;
        }

        return new ReplyMessageInfo(
                replyMessage.getId(),
                resolveSender(replyMessage),
                replyMessage.getContent()
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

    private void handleQuestEventSafely(UUID userId, QuestEventType eventType) {
        try {
            questProgressService.handleEvent(userId, eventType);
        } catch (RuntimeException e) {
            log.warn("퀘스트 진행도 반영 실패 userId={}, eventType={}", userId, eventType, e);
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
