package com.solv.wefin.domain.chat.aiChat.service;


import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiChatServiceTest {

    private OpenAiChatClient openAiChatClient;
    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        openAiChatClient = mock(OpenAiChatClient.class);
        aiChatService = new AiChatService(openAiChatClient);
    }

    @Test
    @DisplayName("ai 채팅 요청 시 답변 정보를 반환한다.")
    void sendMessage_success() {
        // given
        AiChatCommand command = new AiChatCommand("삼성전자 전망 알려줘");
        when(openAiChatClient.ask("삼성전자 전망 알려줘"))
                .thenReturn("삼성전자는 최근 실적 기준으로...");

        // when
        AiChatInfo result = aiChatService.sendMessage(command);

        // then
        assertEquals("삼성전자는 최근 실적 기준으로...", result.answer());
    }

    @Test
    @DisplayName("메시지가 비어있을 시 예외가 발생한다.")
    void sendMessage_fail_blank() {
        // given
        AiChatCommand command = new AiChatCommand(" ");

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command));

        // then
        assertEquals(ErrorCode.CHAT_MESSAGE_EMPTY, exception.getErrorCode());
    }

    @Test
    @DisplayName("AI 클라이언트 호출 실패 시 예외를 발생한다.")
    void sendMessage_fail_when_client_throws() {
        // given
        AiChatCommand command = new AiChatCommand("질문");
        when(openAiChatClient.ask("질문"))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_REQUEST_FAILED));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command));

        // then
        assertEquals(ErrorCode.AI_CHAT_REQUEST_FAILED, exception.getErrorCode());
    }
}
