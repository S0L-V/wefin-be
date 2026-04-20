package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.dto.command.ShareNewsCommand;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.NewsShareInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ReplyMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.VoteShareInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.VoteShareOptionInfo;
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
import org.springframework.transaction.annotation.Propagation;
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

    private static final long SPAM_WINDOW_SECONDS = 3L;
    private static final String WEFINI_COMMAND_PREFIX = "/wefini";
    private static final String YOUNG_COMMAND_LITERAL = "/영";
    private static final String YOUNG_DISPLAY_MESSAGE_LITERAL = "영";
    private static final String YOUNG_RESPONSE_LITERAL = "차";
    private static final String WEFINI_FAILURE_MESSAGE = "AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final String WEFINI_USAGE_MESSAGE = "/wefini 뒤에 질문을 함께 입력해 주세요.";
    private static final String SYSTEM = "시스템";
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatSpamGuard chatSpamGuard;
    private final QuestProgressService questProgressService;
    private final VoteRepository voteRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final OpenAiChatClient openAiChatClient;
    private final ChatMessageWriteService chatMessageWriteService;
    private final NewsClusterRepository newsClusterRepository;
    private final ChatMessageNewsShareService chatMessageNewsShareService;

    private final Map<String, Object> chatLocks = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendMessage(String content, UUID userId, Long replyToMessageId) {
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

            if (handleCommandMessage(content, userId, user, group)) {
                return;
            }

            ChatMessage replyTarget = findReplyTarget(replyToMessageId, groupId);
            chatMessageWriteService.publishUserMessage(user, group, content, replyTarget);
        }

        questProgressService.handleEvent(userId, QuestEventType.SEND_GROUP_CHAT);
    }

    private boolean handleCommandMessage(String content, UUID userId, User user, Group group) {
        String trimmed = content.trim();

        if (trimmed.equals(YOUNG_COMMAND_LITERAL)) {
            chatMessageWriteService.publishUserMessage(user, group, YOUNG_DISPLAY_MESSAGE_LITERAL, null);
            chatMessageWriteService.publishSystemMessage(group, YOUNG_RESPONSE_LITERAL);
            handleQuestEventSafely(userId, QuestEventType.SEND_GROUP_CHAT);
            return true;
        }

        if (trimmed.equals(WEFINI_COMMAND_PREFIX)) {
            chatMessageWriteService.publishSystemMessage(group, WEFINI_USAGE_MESSAGE);
            return true;
        }

        if (!trimmed.startsWith(WEFINI_COMMAND_PREFIX + " ")) {
            return false;
        }

        String question = trimmed.substring(WEFINI_COMMAND_PREFIX.length()).trim();
        if (question.isBlank()) {
            chatMessageWriteService.publishSystemMessage(group, WEFINI_USAGE_MESSAGE);
            return true;
        }

        chatMessageWriteService.publishUserMessage(user, group, trimmed, null);

        String answer;
        try {
            answer = openAiChatClient.ask(List.of(), question, null);
        } catch (BusinessException e) {
            log.warn("AI 응답 실패 userId={}, code={}", userId, e.getErrorCode(), e);
            chatMessageWriteService.publishSystemMessage(group, WEFINI_FAILURE_MESSAGE);
            return true;
        }

        chatMessageWriteService.publishSystemMessage(group, answer);
        handleQuestEventSafely(userId, QuestEventType.USE_AI_CHAT);
        handleQuestEventSafely(userId, QuestEventType.SEND_GROUP_CHAT);
        return true;
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
                : null;

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
        if (replyMessage == null) {
            return null;
        }

        return new ReplyMessageInfo(
                replyMessage.getId(),
                resolveSender(replyMessage),
                replyMessage.getContent()
        );
    }

    private void validateShareNewsCommand(ShareNewsCommand command) {
        if (command == null || command.newsClusterId() == null) {
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
        if (replyToMessageId == null) {
            return null;
        }

        return chatMessageRepository.findByIdAndGroup_Id(replyToMessageId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }
}
