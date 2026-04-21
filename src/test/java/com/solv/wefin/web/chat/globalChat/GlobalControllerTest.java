package com.solv.wefin.web.chat.globalChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.chat.common.service.ChatReadStateService;
import com.solv.wefin.domain.chat.globalChat.dto.info.GlobalChatMessagesInfo;
import com.solv.wefin.domain.chat.globalChat.dto.command.GlobalProfitShareCommand;
import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.config.SecurityConfig;
import com.solv.wefin.global.config.security.JwtAuthenticationEntryPoint;
import com.solv.wefin.global.config.security.JwtAuthenticationFilter;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.global.error.GlobalExceptionHandler;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalProfitShareRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalChatController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class
})
class GlobalChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GlobalChatService globalChatService;

    @MockitoBean
    private ChatReadStateService chatReadStateService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("비로그인 사용자도 전체 채팅 메시지를 조회할 수 있다")
    void getRecentMessages_success_withoutAuthentication() throws Exception {
        GlobalChatMessagesInfo info = new GlobalChatMessagesInfo(
                java.util.List.of(),
                null,
                false
        );

        org.mockito.BDDMockito.given(globalChatService.getMessages(null, 30))
                .willReturn(info);

        mockMvc.perform(get("/api/chat/global/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("수익 공유 요청이 들어오면 command로 변환해 서비스에 전달한다")
    void sendProfitShareMessage_success() throws Exception {
        // given
        GlobalProfitShareRequest request = GlobalProfitShareRequest.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(523000L)
                .build();

        // when
        mockMvc.perform(post("/api/chat/global/profit-share")
                        .with(csrf())
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // then
        verify(globalChatService).sendProfitShareMessage(argThat(command ->
                command.type().equals("PROFIT_ALERT")
                        && command.userNickname().equals("tico")
                        && command.stockName().equals("삼성전자")
                        && command.profitAmount().equals(523000L)
        ));
    }

    @Test
    @DisplayName("profitAmount가 0이면 400 에러를 반환한다")
    void sendProfitShareMessage_fail_when_profitAmount_zero() throws Exception {
        // given
        GlobalProfitShareRequest request = GlobalProfitShareRequest.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(0L)
                .build();

        doThrow(new BusinessException(ErrorCode.INVALID_PROFIT_AMOUNT))
                .when(globalChatService)
                .sendProfitShareMessage(argThat(GlobalProfitShareCommand.class::isInstance));

        // when // then
        mockMvc.perform(post("/api/chat/global/profit-share")
                        .with(csrf())
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PROFIT_AMOUNT"));
    }

    @Test
    @DisplayName("profitAmount가 null이면 validation 에러를 반환한다")
    void sendProfitShareMessage_fail_when_profitAmount_null() throws Exception {
        // given
        String requestBody = """
                {
                  "type": "PROFIT_ALERT",
                  "userNickname": "tico",
                  "stockName": "삼성전자",
                  "profitAmount": null
                }
                """;

        // when // then
        mockMvc.perform(post("/api/chat/global/profit-share")
                        .with(csrf())
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("markRead updates global chat read state")
    void markRead_success() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/chat/global/read")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(chatReadStateService).markGlobalChatRead(userId);
    }
}
