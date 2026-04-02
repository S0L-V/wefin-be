package com.solv.wefin.web.group;

import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.service.GroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupService groupService;

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
}