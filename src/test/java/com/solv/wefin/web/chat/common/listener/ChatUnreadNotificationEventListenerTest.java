package com.solv.wefin.web.chat.common.listener;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;
import com.solv.wefin.domain.chat.common.service.ChatReadStateService;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.web.chat.common.dto.response.ChatUnreadNotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ChatUnreadNotificationEventListenerTest {

    private SimpMessagingTemplate messagingTemplate;
    private ChatReadStateService chatReadStateService;
    private UserRepository userRepository;
    private GroupMemberRepository groupMemberRepository;
    private ChatUnreadNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatReadStateService = mock(ChatReadStateService.class);
        userRepository = mock(UserRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);

        listener = new ChatUnreadNotificationEventListener(
                messagingTemplate,
                chatReadStateService,
                userRepository,
                groupMemberRepository
        );
    }

    @Test
    @DisplayName("전체 채팅 이벤트가 오면 발신자를 제외한 사용자에게 unread 알림을 보낸다")
    void handleGlobalChatCreated_success() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        when(userRepository.findAllActiveUserIds()).thenReturn(List.of(senderId, receiverId));
        when(chatReadStateService.getUnreadInfoSnapshot(receiverId))
                .thenReturn(new ChatUnreadInfo(3L, 1L, 44L, 55L));

        GlobalChatMessageCreatedEvent event = new GlobalChatMessageCreatedEvent(
                11L,
                senderId,
                "USER",
                "보낸사람",
                "전체 채팅 메시지",
                OffsetDateTime.now()
        );

        ArgumentCaptor<ChatUnreadNotificationResponse> payloadCaptor =
                ArgumentCaptor.forClass(ChatUnreadNotificationResponse.class);

        listener.handleGlobalChatCreated(event);

        verify(messagingTemplate).convertAndSendToUser(
                eq(receiverId.toString()),
                eq("/queue/chat-unread"),
                payloadCaptor.capture()
        );
        verify(chatReadStateService, never()).getUnreadInfoSnapshot(senderId);

        ChatUnreadNotificationResponse payload = payloadCaptor.getValue();
        assertEquals("GLOBAL", payload.chatType());
        assertEquals(11L, payload.messageId());
        assertNull(payload.groupId());
        assertEquals("보낸사람", payload.sender());
        assertEquals("전체 채팅 메시지", payload.content());
        assertEquals(4L, payload.totalUnreadCount());
        assertEquals(44L, payload.lastReadGlobalMessageId());
        assertEquals(55L, payload.lastReadGroupMessageId());
    }

    @Test
    @DisplayName("그룹 채팅 이벤트가 오면 발신자를 제외한 그룹 멤버에게 unread 알림을 보낸다")
    void handleGroupChatCreated_success() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        when(groupMemberRepository.findUserIdsByGroupIdAndStatus(5L, GroupMember.GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(senderId, receiverId));
        when(chatReadStateService.getUnreadInfoSnapshot(receiverId))
                .thenReturn(new ChatUnreadInfo(1L, 2L, 66L, 77L));

        ChatMessageInfo message = new ChatMessageInfo(
                31L,
                senderId,
                5L,
                "CHAT",
                "그룹유저",
                "그룹 채팅 메시지",
                OffsetDateTime.now(),
                null,
                null,
                null
        );

        ArgumentCaptor<ChatUnreadNotificationResponse> payloadCaptor =
                ArgumentCaptor.forClass(ChatUnreadNotificationResponse.class);

        listener.handleGroupChatCreated(new ChatMessageCreatedEvent(5L, message));

        verify(messagingTemplate).convertAndSendToUser(
                eq(receiverId.toString()),
                eq("/queue/chat-unread"),
                payloadCaptor.capture()
        );
        verify(chatReadStateService, never()).getUnreadInfoSnapshot(senderId);

        ChatUnreadNotificationResponse payload = payloadCaptor.getValue();
        assertEquals("GROUP", payload.chatType());
        assertEquals(31L, payload.messageId());
        assertEquals(5L, payload.groupId());
        assertEquals("그룹유저", payload.sender());
        assertEquals("그룹 채팅 메시지", payload.content());
        assertEquals(2L, payload.groupUnreadCount());
        assertEquals(66L, payload.lastReadGlobalMessageId());
        assertEquals(77L, payload.lastReadGroupMessageId());
    }
}
