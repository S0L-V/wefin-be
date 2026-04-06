package com.solv.wefin.web.chat.aiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.service.AiChatService;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.global.error.GlobalExceptionHandler;
import com.solv.wefin.web.chat.aiChat.dto.request.AiChatRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
@Import(GlobalExceptionHandler.class)
public class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiChatService aiChatService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("AI 채팅 요청이 들어오면 답변을 반환한다")
    void sendMessage_success() throws Exception {
        // given
        AiChatRequest request = new AiChatRequest("삼성전자 전망 알려줘");
        when(aiChatService.sendMessage(any(AiChatCommand.class)))
                .thenReturn(new AiChatInfo("최근 실적 기준으로..."));

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                    .with(csrf())
                    .with(user("test"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.answer").value("최근 실적 기준으로..."));
    }

    @Test
    @DisplayName("입력 메시지가 비어 있으면 INVALID_INPUT으로 400을 반환한다.")
    void sendMessage_fail_blank() throws Exception {
        // given
        AiChatRequest request = new AiChatRequest(" ");

        when(aiChatService.sendMessage(any(AiChatCommand.class)))
                .thenThrow(new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY));

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                    .with(csrf())
                    .with(user("test"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("AI 응답 시간이 초과되면 504를 반환한다.")
    void sendMessage_fail_timeout() throws Exception {
        // given
        AiChatRequest request = new AiChatRequest("질문");

        when(aiChatService.sendMessage(any(AiChatCommand.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_TIMEOUT));

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                .with(csrf())
                .with(user("test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.code").value("AI_CHAT_TIMEOUT"));
    }
}
