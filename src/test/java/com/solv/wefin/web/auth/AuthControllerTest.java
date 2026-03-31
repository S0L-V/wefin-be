package com.solv.wefin.web.auth;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.auth.dto.SignupResponse;
import com.solv.wefin.domain.auth.service.AuthService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(AuthExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Nested
    @DisplayName("POST /api/auth/signup")
    class SignupTest {

        private String signupRequest(String email, String password, String nickname) {
            return """
            {
              "email": "%s",
              "password": "%s",
              "nickname": "%s"
            }
            """.formatted(email, password, nickname);
        }

        @Test
        @DisplayName("회원가입 성공 시 200과 응답 데이터를 반환한다")
        void signup_success() throws Exception {
            UUID userId = UUID.randomUUID();

            SignupResponse response = SignupResponse.builder()
                    .userId(userId)
                    .email("test@example.com")
                    .nickname("testuser")
                    .build();

            when(authService.signup(
                    eq("test@example.com"),
                    eq("testuser"),
                    eq("pass1234")
            )).thenReturn(response);

            String requestBody = signupRequest("test@example.com", "pass1234", "testuser");

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"));

            verify(authService).signup("test@example.com", "testuser", "pass1234");
        }

        @Test
        @DisplayName("이메일 형식이 잘못되면 validation 에러를 반환한다")
        void signup_fail_when_email_invalid() throws Exception {
            String requestBody = """
                    {
                      "email": "invalid-email",
                      "password": "pass1234",
                      "nickname": "testuser"
                    }
                    """;

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.email").value("올바른 이메일 형식이 아닙니다."));
        }

        @Test
        @DisplayName("여러 필드가 동시에 잘못되면 필드별 에러 메시지를 모두 반환한다")
        void signup_fail_when_multiple_fields_invalid() throws Exception {
            String requestBody = """
                    {
                      "email": "wrong-email",
                      "password": "1234",
                      "nickname": ""
                    }
                    """;

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.email").value("올바른 이메일 형식이 아닙니다."))
                    .andExpect(jsonPath("$.data.password").exists())
                    .andExpect(jsonPath("$.data.nickname").value("닉네임은 필수입니다."));
        }

        @Test
        @DisplayName("이메일이 이미 존재하면 409와 에러 응답을 반환한다")
        void signup_fail_when_email_duplicated() throws Exception {
            // given
            when(authService.signup(
                    eq("test@example.com"),
                    eq("testuser"),
                    eq("pass1234")
            )).thenThrow(new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED));

            String requestBody = signupRequest("test@example.com", "pass1234", "testuser");

            // when & then
            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("AUTH_EMAIL_DUPLICATED"))
                    .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
        }

        @Test
        @DisplayName("닉네임이 이미 존재하면 409와 에러 응답을 반환한다")
        void signup_fail_when_nickname_duplicated() throws Exception {
            // given
            when(authService.signup(
                    eq("test@example.com"),
                    eq("testuser"),
                    eq("pass1234")
            )).thenThrow(new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED));

            String requestBody = signupRequest("test@example.com", "pass1234", "testuser");

            // when & then
            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("AUTH_NICKNAME_DUPLICATED"))
                    .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
        }
    }
}