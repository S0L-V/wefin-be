package com.solv.wefin.web.chat.groupChat;

import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;
import com.solv.wefin.domain.chat.groupChat.service.ChatMessageService;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupChatController.class)
@Import(GlobalExceptionHandler.class)
class GroupChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageService chatMessageService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("현재 사용자의 그룹 최근 메시지를 응답으로 반환한다")
    void getRecentMessages_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        ChatMessageInfo info = new ChatMessageInfo(
                1L,
                userId,
                3L,
                "CHAT",
                "groupUser",
                "안녕하세요",
                OffsetDateTime.now(),
                null
        );

        when(chatMessageService.getMessages(userId, null, 30))
                .thenReturn(new ChatMessagesInfo(
                        List.of(info),
                        null,
                        false
                ));

        // when // then
        mockMvc.perform(get("/api/chat/group/messages")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.messages[0].messageId").value(1))
                .andExpect(jsonPath("$.data.messages[0].groupId").value(3))
                .andExpect(jsonPath("$.data.messages[0].sender").value("groupUser"))
                .andExpect(jsonPath("$.data.messages[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 최근 메시지 조회 시 403을 반환한다")
    void getRecentMessages_fail_when_group_member_forbidden() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        doThrow(new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN))
                .when(chatMessageService)
                .getMessages(userId, null, 30);

        // when // then
        mockMvc.perform(get("/api/chat/group/messages")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_FORBIDDEN"));
    }

    @Test
    @DisplayName("내 그룹 메타 정보를 반환한다")
    void getMyGroup_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        Group group = Group.builder()
                .name("우리 그룹")
                .build();
        ReflectionTestUtils.setField(group, "id", 7L);

        when(chatMessageService.getMyGroup(userId)).thenReturn(group);

        // when // then
        mockMvc.perform(get("/api/chat/group/me")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.groupId").value(7))
                .andExpect(jsonPath("$.data.groupName").value("우리 그룹"));
    }
}