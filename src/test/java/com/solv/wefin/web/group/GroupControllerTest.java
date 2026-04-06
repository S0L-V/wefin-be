package com.solv.wefin.web.group;

import com.solv.wefin.domain.group.dto.GroupInviteInfo;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.entity.GroupInvite;
import com.solv.wefin.domain.group.service.GroupService;
import com.solv.wefin.global.config.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest(GroupController.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @WithMockUser
    @DisplayName("그룹 멤버 목록 조회에 성공한다")
    void getGroupMembers_success() throws Exception {
        // given
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        given(groupService.getActiveMembers(1L))
                .willReturn(List.of(
                        GroupMemberInfo.builder()
                                .userId(leaderId)
                                .nickname("리더")
                                .role("LEADER")
                                .build(),
                        GroupMemberInfo.builder()
                                .userId(memberId)
                                .nickname("멤버")
                                .role("MEMBER")
                                .build()
                ));

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}/members", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(leaderId.toString()))
                .andExpect(jsonPath("$.data[0].nickname").value("리더"))
                .andExpect(jsonPath("$.data[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data[1].userId").value(memberId.toString()))
                .andExpect(jsonPath("$.data[1].nickname").value("멤버"))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"));
    }

    @Test
    @DisplayName("그룹 초대 코드 생성에 성공한다")
    void createInviteCode_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        OffsetDateTime expiredAt = OffsetDateTime.now().plusHours(24);

        given(groupService.createInviteCode(1L, userId))
                .willReturn(new GroupInviteInfo(
                        10L,
                        1L,
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        GroupInvite.InviteStatus.PENDING,
                        expiredAt
                ));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/invite-codes", 1L)
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.codeId").value(10))
                .andExpect(jsonPath("$.data.groupId").value(1))
                .andExpect(jsonPath("$.data.inviteCode").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expiredAt").exists());
    }
}