package com.solv.wefin.web.auth;

import com.solv.wefin.domain.auth.dto.LoginInfo;
import com.solv.wefin.domain.auth.dto.SignupCommand;
import com.solv.wefin.domain.auth.dto.SignupInfo;
import com.solv.wefin.domain.auth.service.AuthService;
import com.solv.wefin.global.config.security.JwtAuthenticationEntryPoint;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
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

import static org.mockito.ArgumentMatchers.any;
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

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

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

            SignupInfo result = new SignupInfo(
                    userId,
                    "test@example.com",
                    "testuser"
            );

            when(authService.signup(any(SignupCommand.class)))
                    .thenReturn(result);

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

            verify(authService).signup(any(SignupCommand.class));
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
            when(authService.signup(any(SignupCommand.class)))
                    .thenThrow(new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED));

            String requestBody = signupRequest("test@example.com", "pass1234", "testuser");

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
            when(authService.signup(any(SignupCommand.class)))
                    .thenThrow(new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED));

            String requestBody = signupRequest("test@example.com", "pass1234", "testuser");

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

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTest {

        private String loginRequest(String email, String password) {
            return """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);
        }

        @Test
        @DisplayName("로그인 성공 시 200과 토큰 응답 데이터를 반환한다")
        void login_success() throws Exception {
            UUID userId = UUID.randomUUID();

            LoginInfo result = new LoginInfo(
                    userId,
                    "testuser",
                    "access-token",
                    "refresh-token"
            );

            when(authService.login("test@example.com", "pass1234"))
                    .thenReturn(result);

            String requestBody = loginRequest("test@example.com", "pass1234");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));

            verify(authService).login("test@example.com", "pass1234");
        }

        @Test
        @DisplayName("이메일 형식이 잘못되면 validation 에러를 반환한다")
        void login_fail_when_email_invalid() throws Exception {
            String requestBody = """
                    {
                      "email": "wrong-email",
                      "password": "pass1234"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
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
        @DisplayName("이메일 또는 비밀번호가 틀리면 401 에러를 반환한다")
        void login_fail_when_login_failed() throws Exception {
            when(authService.login("test@example.com", "wrongpass"))
                    .thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

            String requestBody = loginRequest("test@example.com", "wrongpass");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"))
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
        }

        @Test
        @DisplayName("잠긴 계정이면 423 에러를 반환한다")
        void login_fail_when_account_locked() throws Exception {
            when(authService.login("test@example.com", "pass1234"))
                    .thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED));

            String requestBody = loginRequest("test@example.com", "pass1234");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.status").value(423))
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                    .andExpect(jsonPath("$.message").value("계정이 잠금 상태입니다."));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshTest {

        private String refreshRequest(String refreshToken) {
            return """
        {
          "refreshToken": "%s"
        }
        """.formatted(refreshToken);
        }

        @Test
        @DisplayName("토큰 재발급 성공 시 200과 새 access token을 반환한다")
        void refresh_success() throws Exception {
            when(authService.refresh("refresh-token"))
                    .thenReturn("new-access-token");

            String requestBody = refreshRequest("refresh-token");

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));

            verify(authService).refresh("refresh-token");
        }

        @Test
        @DisplayName("refreshToken이 비어 있으면 validation 에러를 반환한다")
        void refresh_fail_when_refresh_token_blank() throws Exception {
            String requestBody = """
                {
                  "refreshToken": ""
                }
                """;

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.refreshToken").value("리프레시 토큰은 필수입니다."));
        }

        @Test
        @DisplayName("유효하지 않은 refresh token이면 401 에러를 반환한다")
        void refresh_fail_when_token_invalid() throws Exception {
            when(authService.refresh("invalid-refresh-token"))
                    .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

            String requestBody = refreshRequest("invalid-refresh-token");

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .with(user("test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                    .andExpect(jsonPath("$.message").value("유효하지 않은 인증 토큰입니다."));
        }
    }
}