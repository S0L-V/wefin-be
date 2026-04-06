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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("AI 채팅 요청이 들어오면 저장된 AI 메시지를 반환한다")
    void sendMessage_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AiChatRequest request = new AiChatRequest("삼성전자 전망 알려줘");
        OffsetDateTime createdAt = OffsetDateTime.now();

        when(aiChatService.sendMessage(any(AiChatCommand.class), eq(userId)))
                .thenReturn(new AiChatInfo(
                        1L,
                        userId,
                        "AI",
                        "최근 실적 기준으로 설명드릴게요.",
                        createdAt
                ));

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                        .with(csrf())
                        .with(user("test"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.messageId").value(1))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.role").value("AI"))
                .andExpect(jsonPath("$.data.content").value("최근 실적 기준으로 설명드릴게요."));
    }

    @Test
    @DisplayName("입력 메시지가 비어 있으면 INVALID_INPUT으로 400을 반환한다")
    void sendMessage_fail_blank() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AiChatRequest request = new AiChatRequest(" ");

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                        .with(csrf())
                        .with(user("test"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("AI 응답 시간이 초과되면 504를 반환한다")
    void sendMessage_fail_timeout() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AiChatRequest request = new AiChatRequest("질문");

        when(aiChatService.sendMessage(any(AiChatCommand.class), eq(userId)))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_TIMEOUT));

        // when // then
        mockMvc.perform(post("/api/chat/ai/messages")
                        .with(csrf())
                        .with(user("test"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.code").value("AI_CHAT_TIMEOUT"));
    }

    @Test
    @DisplayName("현재 사용자의 AI 채팅 메시지 목록을 반환한다")
    void getMessages_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();

        when(aiChatService.getMessages(userId))
                .thenReturn(List.of(
                        new AiChatInfo(
                                1L,
                                userId,
                                "USER",
                                "삼성전자 어때?",
                                createdAt.minusMinutes(1)
                        ),
                        new AiChatInfo(
                                2L,
                                userId,
                                "AI",
                                "최근 실적 기준으로 설명드릴게요.",
                                createdAt
                        )
                ));

        // when // then
        mockMvc.perform(get("/api/chat/ai/messages")
                        .with(user("test"))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].messageId").value(1))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("삼성전자 어때?"))
                .andExpect(jsonPath("$.data[1].messageId").value(2))
                .andExpect(jsonPath("$.data[1].role").value("AI"))
                .andExpect(jsonPath("$.data[1].content").value("최근 실적 기준으로 설명드릴게요."));
    }
}
