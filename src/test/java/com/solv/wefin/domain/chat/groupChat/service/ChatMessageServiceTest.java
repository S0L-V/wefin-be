package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
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

        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                userRepository,
                eventPublisher,
                groupMemberRepository,
                chatSpamGuard,
                questProgressService,
                newsClusterRepository,
                chatMessageNewsShareService
        );
    }

    @Test
    @DisplayName("그룹 채팅 메시지 전송 시 저장 후 이벤트를 발행한다")
    void sendMessage_success() {
        // given
        UUID userId = UUID.randomUUID();
        String content = "안녕하세요";

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("1조")
                .build();
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

        // when
        chatMessageService.sendMessage(content, userId, null);

        // then
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
    @DisplayName("메시지가 비어 있으면 예외가 발생한다")
    void sendMessage_fail_blank() {
        // given
        UUID userId = UUID.randomUUID();

        // when // then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage(" ", userId, null));

        assertEquals(ErrorCode.CHAT_MESSAGE_EMPTY, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("활성 그룹 멤버가 아니면 예외가 발생한다")
    void sendMessage_fail_when_group_member_not_found() {
        // given
        UUID userId = UUID.randomUUID();

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        // when // then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage("안녕하세요", userId, null));

        assertEquals(ErrorCode.GROUP_MEMBER_FORBIDDEN, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("최근 메시지 조회 시 현재 사용자의 그룹 메시지만 반환한다")
    void getRecentMessages_success() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("1조")
                .build();
        ReflectionTestUtils.setField(group, "id", 3L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        ChatMessage message = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("최근 메시지")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(message, "id", 7L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.findMessagesByGroupId(eq(3L), any(Pageable.class)))
                .thenReturn(List.of(message));

        // when
        ChatMessagesInfo result = chatMessageService.getMessages(userId, null, 30);

        // then
        assertEquals(1, result.messages().size());
        assertEquals(7L, result.messages().get(0).messageId());
        assertEquals(3L, result.messages().get(0).groupId());
        assertEquals("groupUser", result.messages().get(0).sender());
        assertEquals("CHAT", result.messages().get(0).messageType());
        assertNull(result.messages().get(0).replyTo());
        assertEquals(false, result.hasNext());
        assertEquals(null, result.nextCursor());
    }

    @Test
    @DisplayName("내 그룹 메타 정보 조회 시 활성 그룹을 반환한다")
    void getMyGroup_success() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("우리 그룹")
                .build();
        ReflectionTestUtils.setField(group, "id", 5L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.LEADER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));

        // when
        Group result = chatMessageService.getMyGroup(userId);

        // then
        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("우리 그룹", result.getName());
    }

    @Test
    @DisplayName("답장 대상 메시지가 있으면 replyToMessage를 저장한다")
    void sendMessage_success_with_reply() {
        // given
        UUID userId = UUID.randomUUID();
        String content = "답장합니다";

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("1조")
                .build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        ChatMessage replyTarget = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("원본 메시지")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(replyTarget, "id", 99L);

        ChatMessage savedMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content(content)
                .replyToMessage(replyTarget)
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 10L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.findByIdAndGroup_Id(99L, 1L))
                .thenReturn(Optional.of(replyTarget));
        when(chatMessageRepository.countByGroup_IdAndUser_UserIdAndCreatedAtAfter(
                eq(1L), eq(userId), any(OffsetDateTime.class))
        ).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);

        // when
        chatMessageService.sendMessage(content, userId, 99L);

        // then
        verify(chatMessageRepository).save(captor.capture());

        ChatMessage capturedMessage = captor.getValue();
        assertEquals(replyTarget, capturedMessage.getReplyToMessage());
    }

    @Test
    @DisplayName("답장 대상 메시지가 없으면 예외가 발생한다")
    void sendMessage_fail_when_reply_target_not_found() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("1조")
                .build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.findByIdAndGroup_Id(99L, 1L))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage("답장", userId, 99L));

        // then
        assertEquals(ErrorCode.CHAT_MESSAGE_NOT_FOUND, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("그룹 채팅 메시지 조회 시 hasNext와 nextCursor를 계산한다")
    void getMessages_success_with_hasNext_and_nextCursor() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("1조")
                .build();
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
                .content("세 번째 메시지")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(latestMessage, "id", 3L);

        ChatMessage middleMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("두 번째 메시지")
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(middleMessage, "id", 2L);

        ChatMessage oldestMessage = ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content("첫 번째 메시지")
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .build();
        ReflectionTestUtils.setField(oldestMessage, "id", 1L);

        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(chatMessageRepository.findMessagesByGroupId(eq(3L), any(Pageable.class)))
                .thenReturn(List.of(latestMessage, middleMessage, oldestMessage));

        // when
        ChatMessagesInfo result = chatMessageService.getMessages(userId, null, 2);

        // then
        assertEquals(2, result.messages().size());
        assertTrue(result.hasNext());
        assertEquals(2L, result.nextCursor());

        assertEquals(2L, result.messages().get(0).messageId());
        assertEquals("두 번째 메시지", result.messages().get(0).content());
        assertEquals(3L, result.messages().get(1).messageId());
        assertEquals("세 번째 메시지", result.messages().get(1).content());
    }

    @Test
    @DisplayName("뉴스 공유 메시지를 저장하고 뉴스 공유 정보를 포함한 응답을 반환한다")
    void shareNews_success() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("group")
                .build();
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

        // when
        var result = chatMessageService.shareNews(userId, new ShareNewsCommand(55L));

        // then
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

    @Test
    @DisplayName("공유할 뉴스 클러스터가 없으면 예외가 발생한다")
    void shareNews_fail_when_news_cluster_not_found() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("groupUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Group group = Group.builder()
                .name("group")
                .build();
        ReflectionTestUtils.setField(group, "id", 1L);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(groupMemberRepository.findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(groupMember));
        when(newsClusterRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatMessageService.shareNews(userId, new ShareNewsCommand(999L))
        );

        // then
        assertEquals(ErrorCode.NEWS_CLUSTER_NOT_FOUND, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any());
        verify(chatMessageNewsShareService, never()).save(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
