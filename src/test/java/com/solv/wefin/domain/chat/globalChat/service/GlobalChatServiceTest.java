package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.entity.Users;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.domain.chat.globalChat.repository.UsersRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GlobalChatServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private GlobalChatMessageRepository globalChatMessageRepository;
    private UsersRepository usersRepository;
    private GlobalChatService globalChatService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        globalChatMessageRepository = mock(GlobalChatMessageRepository.class);
        usersRepository = mock(UsersRepository.class);

        globalChatService = new GlobalChatService(
                messagingTemplate,
                globalChatMessageRepository,
                usersRepository
        );
    }

    @Test
    @DisplayName("전체 채팅 사용자 메시지 전송 시 저장 후 브로드캐스트된다")
    void sendMessage_success() {

        UUID userId = UUID.randomUUID();

        GlobalChatSendRequest request = new GlobalChatSendRequest();
        ReflectionTestUtils.setField(request, "content", "안녕하세요");

        Users user = Users.builder()
                .id(userId)
                .email("test1@test.com")
                .nickname("testUser")
                .password("password1")
                .build();

        GlobalChatMessage savedMessage = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content("안녕하세요")
                .createdAt(LocalDateTime.now())
                .build();

        ReflectionTestUtils.setField(savedMessage, "id", 1L);

        when(usersRepository.findById(userId)).thenReturn(Optional.of(user));
        when(globalChatMessageRepository.save(any(GlobalChatMessage.class))).thenReturn(savedMessage);

        globalChatService.sendMessage(request, userId);

        verify(usersRepository, times(1)).findById(userId);
        verify(globalChatMessageRepository, times(1)).save(any(GlobalChatMessage.class));
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/chat/global"), any(GlobalChatMessageResponse.class));

    }

    @Test
    @DisplayName("메시지가 비어있으면 예외가 발생한다")
    void sendMessage_fail_blank() {

        UUID userId = UUID.randomUUID();

        GlobalChatSendRequest request = new GlobalChatSendRequest();
        ReflectionTestUtils.setField(request, "content", " ");

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(request, userId));
    }

    @Test
    @DisplayName("메시지가 1000자를 초과하면 예외가 발생한다")
    void sendMessage_fail_tooLong() {

        UUID userId = UUID.randomUUID();

        GlobalChatSendRequest request = new GlobalChatSendRequest();
        String longMessage = "a".repeat(1001);
        ReflectionTestUtils.setField(request, "content", longMessage);

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(request, userId));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void sendMessage_fail_userNotFound() {

        UUID userId = UUID.randomUUID();

        GlobalChatSendRequest request = new GlobalChatSendRequest();
        ReflectionTestUtils.setField(request, "content", "안녕하세요");

        when(usersRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(request, userId));

        verify(globalChatMessageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GlobalChatMessageResponse.class));
    }

    @Test
    @DisplayName("최근 메시지 조회 시 응답 DTO로 변환된다")
    void getRecentMessages_success() {

        UUID userId = UUID.randomUUID();

        Users user = Users.builder()
                .id(userId)
                .email("test1@test.com")
                .nickname("testUser1")
                .password("password1")
                .build();

        GlobalChatMessage message = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content("최근 메시지")
                .createdAt(LocalDateTime.now())
                .build();

        ReflectionTestUtils.setField(message, "id", 10L);

        when(globalChatMessageRepository.findRecentMessages(any()))
                .thenReturn(List.of(message));

        var result = globalChatService.getRecentMessages();

        assertEquals(1, result.size());
        assertEquals("testUser1", result.get(0).getSender());
        assertEquals("USER", result.get(0).getRole());
    }
}
