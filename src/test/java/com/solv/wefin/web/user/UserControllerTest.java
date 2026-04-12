package com.solv.wefin.web.user;

import com.solv.wefin.domain.group.dto.MyActiveGroupInfo;
import com.solv.wefin.domain.group.service.GroupService;
import com.solv.wefin.domain.user.dto.MyPageInfo;
import com.solv.wefin.domain.user.service.UserService;
import com.solv.wefin.global.config.SecurityConfig;
import com.solv.wefin.global.config.security.JwtAuthenticationEntryPoint;
import com.solv.wefin.global.config.security.JwtAuthenticationFilter;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증된 사용자는 마이페이지를 조회할 수 있다")
    void getMyPage_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String token = "valid-access-token";
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-01T09:00:00+09:00");

        MyPageInfo info = new MyPageInfo(
                userId,
                "user@example.com",
                "테스트유저",
                createdAt
        );

        given(jwtProvider.isValid(token)).willReturn(true);
        given(jwtProvider.isAccessToken(token)).willReturn(true);
        given(jwtProvider.getUserId(token)).willReturn(userId);
        given(userService.getMyPage(userId)).willReturn(info);

        // when & then
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-01T09:00:00+09:00"));
    }

    @Test
    @DisplayName("인증된 사용자는 현재 활성 그룹을 조회할 수 있다")
    void getMyActiveGroup_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String token = "valid-access-token";

        MyActiveGroupInfo info = new MyActiveGroupInfo(
                1L,
                "테스트유저의 그룹",
                true
        );

        given(jwtProvider.isValid(token)).willReturn(true);
        given(jwtProvider.isAccessToken(token)).willReturn(true);
        given(jwtProvider.getUserId(token)).willReturn(userId);
        given(groupService.getMyActiveGroup(userId)).willReturn(info);

        // when & then
        mockMvc.perform(get("/api/users/me/group")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(1L))
                .andExpect(jsonPath("$.data.groupName").value("테스트유저의 그룹"))
                .andExpect(jsonPath("$.data.isHomeGroup").value(true));
    }

    @Test
    @DisplayName("현재 활성 그룹 조회 시 인증 헤더가 없으면 401을 반환한다")
    void getMyActiveGroup_unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me/group"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("현재 활성 그룹 조회 시 유효하지 않은 토큰이면 401을 반환한다")
    void getMyActiveGroup_invalidToken() throws Exception {
        // given
        String token = "invalid-token";

        given(jwtProvider.isValid(token)).willReturn(false);

        // when & then
        mockMvc.perform(get("/api/users/me/group")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 토큰입니다."));
    }

    @Test
    @DisplayName("현재 활성 그룹이 없으면 404를 반환한다")
    void getMyActiveGroup_groupMemberNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String token = "valid-access-token";

        given(jwtProvider.isValid(token)).willReturn(true);
        given(jwtProvider.isAccessToken(token)).willReturn(true);
        given(jwtProvider.getUserId(token)).willReturn(userId);
        given(groupService.getMyActiveGroup(userId))
                .willThrow(new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/users/me/group")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("그룹 멤버를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환한다")
    void getMyPage_unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 401을 반환한다")
    void getMyPage_invalidToken() throws Exception {
        // given
        String token = "invalid-token";

        given(jwtProvider.isValid(token)).willReturn(false);

        // when & then
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 토큰입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void getMyPage_userNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String token = "valid-access-token";

        given(jwtProvider.isValid(token)).willReturn(true);
        given(jwtProvider.isAccessToken(token)).willReturn(true);
        given(jwtProvider.getUserId(token)).willReturn(userId);
        given(userService.getMyPage(userId))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }
}