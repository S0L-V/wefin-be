package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GlobalChatServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private GlobalChatService globalChatService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        globalChatService = new GlobalChatService(messagingTemplate);
    }

    @Test
    @DisplayName("전체 채팅 메시지 전송 시 브로드캐스트된다")
    void sendMessage_success() {
        GlobalChatSendRequest request = new GlobalChatSendRequest();
        ReflectionTestUtils.setField(request, "content", "안녕하세요");

        globalChatService.sendMessage(request, "tico");

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/chat/global"), any(GlobalChatMessageResponse.class));

    }

    @Test
    @DisplayName("메시지가 비어있으면 예외가 발생한다")
    void sendMessage_fail_blank() {
        GlobalChatSendRequest request = new GlobalChatSendRequest();
        ReflectionTestUtils.setField(request, "content", " ");

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(request, "tico"));
    }

    @Test
    @DisplayName("메시지가 1000자를 초과하면 예외가 발생한다")
    void sendMessage_fail_tooLong() {
        GlobalChatSendRequest request = new GlobalChatSendRequest();
        String longMessage = "a".repeat(1001);
        ReflectionTestUtils.setField(request, "content", longMessage);

        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(request, "tico"));
    }
}
