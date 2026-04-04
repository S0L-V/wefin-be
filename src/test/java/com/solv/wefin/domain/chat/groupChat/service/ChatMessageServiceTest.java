package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.MessageType;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @BeforeEach
    void setUp() {
        chatMessageRepository = mock(ChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        chatSpamGuard = mock(ChatSpamGuard.class);

        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                userRepository,
                eventPublisher,
                groupMemberRepository,
                chatSpamGuard
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
        when(chatMessageRepository.findRecentMessagesByGroupId(eq(3L), any(Pageable.class)))
                .thenReturn(List.of(message));

        // when
        List<ChatMessageInfo> result = chatMessageService.getRecentMessages(userId, 50);

        // then
        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).messageId());
        assertEquals(3L, result.get(0).groupId());
        assertEquals("groupUser", result.get(0).sender());
        assertEquals("CHAT", result.get(0).messageType());
        assertNull(result.get(0).replyTo());
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

}
