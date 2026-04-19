package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.dto.command.ShareNewsCommand;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;
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
import com.solv.wefin.domain.quest.entity.QuestEventType;
import com.solv.wefin.domain.quest.service.QuestProgressService;
import com.solv.wefin.domain.vote.repository.VoteOptionRepository;
import com.solv.wefin.domain.vote.repository.VoteRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceTest {

    private ChatMessageRepository chatMessageRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private GroupMemberRepository groupMemberRepository;
    private ChatSpamGuard chatSpamGuard;
    private ChatMessageService chatMessageService;
    private NewsClusterRepository newsClusterRepository;
    private ChatMessageNewsShareService chatMessageNewsShareService;
    private QuestProgressService questProgressService;
    private VoteRepository voteRepository;
    private VoteOptionRepository voteOptionRepository;
    private OpenAiChatClient openAiChatClient;

    @BeforeEach
    void setUp() {
        chatMessageRepository = mock(ChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        chatSpamGuard = mock(ChatSpamGuard.class);
        newsClusterRepository = mock(NewsClusterRepository.class);
        chatMessageNewsShareService = mock(ChatMessageNewsShareService.class);
        questProgressService = mock(QuestProgressService.class);
        voteRepository = mock(VoteRepository.class);
        voteOptionRepository = mock(VoteOptionRepository.class);
        openAiChatClient = mock(OpenAiChatClient.class);

        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                userRepository,
                eventPublisher,
                groupMemberRepository,
                chatSpamGuard,
                questProgressService,
                voteRepository,
                voteOptionRepository,
                openAiChatClient,
                newsClusterRepository,
                chatMessageNewsShareService
        );
    }

    @Test
    @DisplayName("sendMessage publishes event and quest progress")
    void sendMessage_success() {
        UUID userId = UUID.randomUUID();
        String content = "hello";

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder().name("group-1").build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        ChatMessage savedMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 10L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.countByGroup_IdAndUser_UserIdAndCreatedAtAfter(
                eq(1L), eq(userId), any(OffsetDateTime.class))
        ).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);

        chatMessageService.sendMessage(content, userId, null);

        verify(chatMessageRepository, times(1))
                .countByGroup_IdAndUser_UserIdAndCreatedAtAfter(eq(1L), eq(userId), any(OffsetDateTime.class));
        verify(chatSpamGuard, times(1))
                .validate(eq(ChatScope.groupKey(1L, userId)), eq(0L), any(OffsetDateTime.class));
        verify(chatMessageRepository).save(captor.capture());
        verify(eventPublisher).publishEvent(any(ChatMessageCreatedEvent.class));
        verify(questProgressService).handleEvent(userId, QuestEventType.SEND_GROUP_CHAT);

        ChatMessage capturedMessage = captor.getValue();
        assertEquals(group, capturedMessage.getGroup());
        assertEquals(user, capturedMessage.getUser());
        assertEquals(MessageType.CHAT, capturedMessage.getMessageType());
        assertEquals(content, capturedMessage.getContent());
        assertNull(capturedMessage.getReplyToMessage());
    }

    @Test
    @DisplayName("sendMessage rejects blank content")
    void sendMessage_fail_blank() {
        UUID userId = UUID.randomUUID();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage(" ", userId, null));

        assertEquals(ErrorCode.CHAT_MESSAGE_EMPTY, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("sendMessage handles /영 command as user message and system message")
    void sendMessage_youngCommand_success() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder().name("group-1").build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        ChatMessage savedUserMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("영")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedUserMessage, "id", 22L);

        ChatMessage savedSystemMessage = ChatMessage.builder()
                .group(group)
                .messageType(MessageType.SYSTEM)
                .content("차")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedSystemMessage, "id", 23L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.countByGroup_IdAndUser_UserIdAndCreatedAtAfter(
                eq(1L), eq(userId), any(OffsetDateTime.class))
        ).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(savedUserMessage, savedSystemMessage);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);

        chatMessageService.sendMessage("/영", userId, null);

        verify(openAiChatClient, never()).ask(any(), any(), any());
        verify(chatMessageRepository, times(2)).save(captor.capture());
        verify(eventPublisher, times(2)).publishEvent(any(ChatMessageCreatedEvent.class));
        verify(questProgressService).handleEvent(userId, QuestEventType.SEND_GROUP_CHAT);

        List<ChatMessage> capturedMessages = captor.getAllValues();
        assertEquals(MessageType.CHAT, capturedMessages.get(0).getMessageType());
        assertEquals("영", capturedMessages.get(0).getContent());
        assertEquals(MessageType.SYSTEM, capturedMessages.get(1).getMessageType());
        assertEquals("차", capturedMessages.get(1).getContent());
        assertNull(capturedMessages.get(1).getUser());
    }

    @Test
    @DisplayName("getMessages computes hasNext and nextCursor")
    void getMessages_success_with_hasNext_and_nextCursor() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder().name("group-1").build();
        ReflectionTestUtils.setField(group, "id", 3L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        ChatMessage latestMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("third")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(latestMessage, "id", 3L);

        ChatMessage middleMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("second")
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(middleMessage, "id", 2L);

        ChatMessage oldestMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("first")
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .build();
        ReflectionTestUtils.setField(oldestMessage, "id", 1L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.findMessagesByGroupId(eq(3L), any(Pageable.class)))
                .thenReturn(List.of(latestMessage, middleMessage, oldestMessage));

        ChatMessagesInfo result = chatMessageService.getMessages(userId, null, 2);

        assertEquals(2, result.messages().size());
        assertTrue(result.hasNext());
        assertEquals(2L, result.nextCursor());
        assertEquals(2L, result.messages().get(0).messageId());
        assertEquals("second", result.messages().get(0).content());
        assertEquals(3L, result.messages().get(1).messageId());
        assertEquals("third", result.messages().get(1).content());
    }

    @Test
    @DisplayName("shareNews returns response with news share payload")
    void shareNews_success() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder().name("group").build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        NewsCluster newsCluster = NewsCluster.createSingle(
                new float[]{1.0f},
                100L,
                "https://image.test/thumb.png",
                OffsetDateTime.now()
        );
        ReflectionTestUtils.setField(newsCluster, "id", 55L);
        ReflectionTestUtils.setField(newsCluster, "title", "cluster title");
        ReflectionTestUtils.setField(newsCluster, "summary", "cluster summary");
        ReflectionTestUtils.setField(newsCluster, "thumbnailUrl", "https://image.test/thumb.png");

        ChatMessage savedMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.NEWS)
                .content("cluster title")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 10L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(newsClusterRepository.findById(55L)).thenReturn(Optional.of(newsCluster));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);
        when(chatMessageNewsShareService.save(savedMessage, newsCluster))
                .thenAnswer(invocation -> {
                    ChatMessageNewsShare newsShare = ChatMessageNewsShare.create(savedMessage, newsCluster);
                    savedMessage.attachNewsShare(newsShare);
                    return newsShare;
                });

        var result = chatMessageService.shareNews(userId, new ShareNewsCommand(55L));

        verify(chatMessageRepository).save(argThat(message ->
                message.getMessageType() == MessageType.NEWS
                        && "cluster title".equals(message.getContent())
                        && message.getReplyToMessage() == null
        ));
        verify(chatMessageNewsShareService).save(savedMessage, newsCluster);
        verify(eventPublisher).publishEvent(any(ChatMessageCreatedEvent.class));
        verify(questProgressService).handleEvent(userId, QuestEventType.SHARE_NEWS);
        verify(questProgressService).handleEvent(userId, QuestEventType.SEND_GROUP_CHAT);

        assertEquals(10L, result.messageId());
        assertEquals("NEWS", result.messageType());
        assertNotNull(result.newsShare());
        assertEquals(55L, result.newsShare().newsClusterId());
        assertEquals("cluster title", result.newsShare().title());
        assertEquals("cluster summary", result.newsShare().summary());
        assertEquals("https://image.test/thumb.png", result.newsShare().thumbnailUrl());
    }
}

