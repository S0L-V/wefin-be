package com.solv.wefin.web.chat.common;

import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;
import com.solv.wefin.domain.chat.common.service.ChatReadStateService;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatReadStateController.class)
@Import(GlobalExceptionHandler.class)
class ChatReadStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatReadStateService chatReadStateService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("unread 조회 시 그룹과 전체 unread 정보를 반환한다")
    void getUnread_success() throws Exception {
        UUID userId = UUID.randomUUID();

        when(chatReadStateService.getUnreadInfo(userId))
                .thenReturn(new ChatUnreadInfo(2L, 5L, 101L, 202L));

        mockMvc.perform(get("/api/chat/unread")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.globalUnreadCount").value(2))
                .andExpect(jsonPath("$.data.groupUnreadCount").value(5))
                .andExpect(jsonPath("$.data.totalUnreadCount").value(7))
                .andExpect(jsonPath("$.data.hasGlobalUnread").value(true))
                .andExpect(jsonPath("$.data.hasGroupUnread").value(true))
                .andExpect(jsonPath("$.data.lastReadGlobalMessageId").value(101))
                .andExpect(jsonPath("$.data.lastReadGroupMessageId").value(202));
    }

    @Test
    @DisplayName("unread 조회 시 인증 사용자 id로 서비스를 호출한다")
    void getUnread_calls_service_with_authenticated_user() throws Exception {
        UUID userId = UUID.randomUUID();

        when(chatReadStateService.getUnreadInfo(userId))
                .thenReturn(new ChatUnreadInfo(0L, 0L, 11L, 22L));

        mockMvc.perform(get("/api/chat/unread")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isOk());

        verify(chatReadStateService).getUnreadInfo(userId);
    }
}
