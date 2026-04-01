package com.solv.wefin.web.chat.globalChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.error.GlobalExceptionHandler;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalProfitShareRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalChatController.class)
@Import(GlobalExceptionHandler.class)
class GlobalChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GlobalChatService globalChatService;

    @Test
    @DisplayName("수익이 양수면 축하 시스템 메시지를 전송한다")
    void sendProfitShareMessage_profit_success() throws Exception {
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
        verify(globalChatService).sendSystemMessage(
                eq("축하합니다! tico님이 삼성전자에서 523000원의 수익을 달성하셨습니다!")
        );
    }

    @Test
    @DisplayName("수익이 0 이하이면 손실 시스템 메시지를 전송한다")
    void sendProfitShareMessage_loss_success() throws Exception {
        // given
        GlobalProfitShareRequest request = GlobalProfitShareRequest.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(-10000L)
                .build();

        // when
        mockMvc.perform(post("/api/chat/global/profit-share")
                        .with(csrf())
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        // then
        verify(globalChatService).sendSystemMessage(
                eq("안타깝네요. tico님이 삼성전자에서 10000원을 잃었습니다.")
        );
    }

    @Test
    @DisplayName("서비스 예외가 발생하면 500 응답을 반환한다")
    void sendProfitShareMessage_fail_when_service_throws() throws Exception {
        // given
        GlobalProfitShareRequest request = GlobalProfitShareRequest.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(523000L)
                .build();

        doThrow(new RuntimeException("test exception"))
                .when(globalChatService).sendSystemMessage(anyString());

        // when
        mockMvc.perform(post("/api/chat/global/profit-share")
                        .with(csrf())
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        // then
    }
}
