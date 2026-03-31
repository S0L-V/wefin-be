package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class GlobalChatServiceTest {

    private ApplicationEventPublisher eventPublisher;
    private GlobalChatMessageRepository globalChatMessageRepository;
    private UserRepository userRepository;
    private GlobalChatService globalChatService;
    private ChatSpamGuard chatSpamGuard;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        globalChatMessageRepository = mock(GlobalChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        chatSpamGuard = mock(ChatSpamGuard.class);

        globalChatService = new GlobalChatService(
                eventPublisher,
                globalChatMessageRepository,
                userRepository,
                chatSpamGuard
        );
    }

    @Test
    @DisplayName("전체 채팅 사용자 메시지 전송 시 이벤트를 발행한다")
    void sendMessage_success() {
        UUID userId = UUID.randomUUID();
        String content = "안녕하세요";

        User user = User.builder()
                .email("test1@test.com")
                .nickname("testUser")
                .password("password1")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        GlobalChatMessage savedMessage = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 1L);

        when(globalChatMessageRepository.countByUser_UserIdAndCreatedAtAfter(
                eq(userId), any(OffsetDateTime.class))
        ).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(globalChatMessageRepository.save(any(GlobalChatMessage.class))).thenReturn(savedMessage);

        globalChatService.sendMessage(content, userId);

        verify(globalChatMessageRepository, times(1))
                .countByUser_UserIdAndCreatedAtAfter(eq(userId), any(OffsetDateTime.class));
        verify(chatSpamGuard, times(1))
                .validate(startsWith(ChatScope.GLOBAL + ":"), eq(0L), any(OffsetDateTime.class));
        verify(userRepository, times(1)).findById(userId);
        verify(globalChatMessageRepository, times(1)).save(any(GlobalChatMessage.class));
        verify(eventPublisher, times(1)).publishEvent(any(GlobalChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("메시지가 비어 있으면 예외가 발생한다")
    void sendMessage_fail_blank() {
        UUID userId = UUID.randomUUID();

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(" ", userId));
    }

    @Test
    @DisplayName("메시지가 1000자를 초과하면 예외가 발생한다")
    void sendMessage_fail_tooLong() {
        UUID userId = UUID.randomUUID();
        String longMessage = "a".repeat(1001);

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(longMessage, userId));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void sendMessage_fail_userNotFound() {
        UUID userId = UUID.randomUUID();
        String content = "안녕하세요";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(content, userId));

        verify(globalChatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(GlobalChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("최근 메시지 조회 시 domain info로 변환한다")
    void getRecentMessages_success() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test1@test.com")
                .nickname("testUser1")
                .password("password1")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        GlobalChatMessage message = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content("최근 메시지")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(message, "id", 10L);

        when(globalChatMessageRepository.findRecentMessages(any()))
                .thenReturn(List.of(message));

        var result = globalChatService.getRecentMessages(1);

        assertEquals(1, result.size());
        assertEquals("testUser1", result.get(0).sender());
        assertEquals("USER", result.get(0).role());
    }
}
