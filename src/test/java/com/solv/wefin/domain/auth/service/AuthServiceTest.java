package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.dto.LoginInfo;
import com.solv.wefin.domain.auth.dto.SignupCommand;
import com.solv.wefin.domain.auth.dto.SignupInfo;
import com.solv.wefin.domain.auth.entity.RefreshToken;
import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.entity.UserStatus;
import com.solv.wefin.domain.auth.repository.RefreshTokenRepository;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.service.GroupService;
import com.solv.wefin.domain.quest.entity.QuestEventType;
import com.solv.wefin.domain.quest.service.QuestProgressService;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GroupService groupService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private QuestProgressService questProgressService;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup")
    class SignupTest {

        @Test
        @DisplayName("회원가입에 성공한다")
        void signup_success() {
            String rawEmail = "  TEST@Example.com  ";
            String rawNickname = "  testuser  ";
            String rawPassword = "pass1234";

            UUID userId = UUID.randomUUID();

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("testuser")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");

            User savedUser = User.builder()
                    .email("test@example.com")
                    .nickname("testuser")
                    .password("encoded-password")
                    .build();

            ReflectionTestUtils.setField(savedUser, "userId", userId);

            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            Group homeGroup = Group.builder()
                    .name("testuser의 그룹")
                    .build();

            when(groupService.createDefaultGroup(savedUser)).thenReturn(homeGroup);

            SignupInfo response = authService.signup(
                    new SignupCommand(rawEmail, rawNickname, rawPassword)
            );

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            verify(groupService).createDefaultGroup(savedUser);

            User capturedUser = captor.getValue();

            assertAll(
                    () -> assertThat(capturedUser.getEmail()).isEqualTo("test@example.com"),
                    () -> assertThat(capturedUser.getNickname()).isEqualTo("testuser"),
                    () -> assertThat(capturedUser.getPassword()).isEqualTo("encoded-password"),
                    () -> assertThat(savedUser.getHomeGroup()).isEqualTo(homeGroup),
                    () -> assertThat(response.userId()).isEqualTo(userId),
                    () -> assertThat(response.email()).isEqualTo("test@example.com"),
                    () -> assertThat(response.nickname()).isEqualTo("testuser")
            );
        }

        @Test
        @DisplayName("이메일이 중복되면 예외가 발생한다")
        void signup_fail_when_email_duplicated() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
            verify(userRepository, never()).existsByNickname(anyString());
            verify(userRepository, never()).save(any(User.class));
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외가 발생한다")
        void signup_fail_when_nickname_duplicated() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(true);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_NICKNAME_DUPLICATED);
            verify(userRepository, never()).save(any(User.class));
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("입력값이 null 또는 blank면 validation 예외가 발생한다")
        void signup_fail_when_input_invalid() {
            BusinessException nullException = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand(null, "nickname", "pass1234")
                    )
            );

            BusinessException blankException = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("   ", "nickname", "pass1234")
                    )
            );

            assertAll(
                    () -> assertThat(nullException.getErrorCode()).isEqualTo(ErrorCode.AUTH_VALIDATION_FAILED),
                    () -> assertThat(blankException.getErrorCode()).isEqualTo(ErrorCode.AUTH_VALIDATION_FAILED)
            );

            verify(userRepository, never()).save(any(User.class));
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("DB 이메일 unique 제약 위반 시 이메일 중복 예외로 변환한다")
        void signup_fail_when_email_constraint_violated() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");

            ConstraintViolationException cause =
                    new ConstraintViolationException("constraint violated", new SQLException(), "uk_users_email");

            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("db error", cause));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("DB 닉네임 unique 제약 위반 시 닉네임 중복 예외로 변환한다")
        void signup_fail_when_nickname_constraint_violated() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");

            ConstraintViolationException cause =
                    new ConstraintViolationException("constraint violated", new SQLException(), "uk_users_nickname");

            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("db error", cause));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_NICKNAME_DUPLICATED);
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTest {

        @Test
        @DisplayName("로그인에 성공하면 access token, refresh token을 발급하고 저장한다")
        void login_success() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(14);

            User user = User.builder()
                    .email("test@example.com")
                    .nickname("testuser")
                    .password("encoded-password")
                    .build();

            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass1234", "encoded-password")).thenReturn(true);
            when(jwtProvider.generateAccessToken(userId)).thenReturn("access-token");
            when(jwtProvider.generateRefreshToken(userId)).thenReturn("refresh-token");
            when(jwtProvider.getExpiration("refresh-token")).thenReturn(expiresAt);
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.empty());

            LoginInfo result = authService.login("test@example.com", "pass1234");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken savedToken = captor.getValue();

            assertAll(
                    () -> assertThat(result.userId()).isEqualTo(userId),
                    () -> assertThat(result.nickname()).isEqualTo("testuser"),
                    () -> assertThat(result.accessToken()).isEqualTo("access-token"),
                    () -> assertThat(result.refreshToken()).isEqualTo("refresh-token"),
                    () -> assertThat(savedToken.getUserId()).isEqualTo(userId),
                    () -> assertThat(savedToken.getToken()).isEqualTo("refresh-token"),
                    () -> assertThat(savedToken.getExpiresAt()).isEqualTo(expiresAt),
                    () -> assertThat(savedToken.isRevoked()).isFalse()
            );
            verify(questProgressService).handleEvent(userId, QuestEventType.LOGIN);
        }

        @Test
        @DisplayName("퀘스트 반영이 실패해도 로그인은 정상적으로 성공한다")
        void login_success_even_when_quest_progress_update_fails() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(14);

            User user = User.builder()
                    .email("test@example.com")
                    .nickname("testuser")
                    .password("encoded-password")
                    .build();

            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass1234", "encoded-password")).thenReturn(true);
            when(jwtProvider.generateAccessToken(userId)).thenReturn("access-token");
            when(jwtProvider.generateRefreshToken(userId)).thenReturn("refresh-token");
            when(jwtProvider.getExpiration("refresh-token")).thenReturn(expiresAt);
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.empty());
            doThrow(new RuntimeException("quest failed"))
                    .when(questProgressService).handleEvent(userId, QuestEventType.LOGIN);

            LoginInfo result = authService.login("test@example.com", "pass1234");

            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(questProgressService).handleEvent(userId, QuestEventType.LOGIN);
            assertAll(
                    () -> assertThat(result.userId()).isEqualTo(userId),
                    () -> assertThat(result.nickname()).isEqualTo("testuser"),
                    () -> assertThat(result.accessToken()).isEqualTo("access-token"),
                    () -> assertThat(result.refreshToken()).isEqualTo("refresh-token")
            );
        }

        @Test
        @DisplayName("이메일이 존재하지 않으면 로그인 실패 예외가 발생한다")
        void login_fail_when_user_not_found() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.login("test@example.com", "pass12324")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 로그인 실패 예외가 발생한다")
        void login_fail_when_password_not_matched() {
            UUID userId = UUID.randomUUID();

            User user = User.builder()
                    .email("test@example.com")
                    .nickname("testuser")
                    .password("encoded-password")
                    .build();

            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-pass", "encoded-password")).thenReturn(false);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.login("test@example.com", "wrong-pass")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("계정이 잠금 상태면 ACCOUNT_LOCKED 예외가 발생한다")
        void login_fail_when_account_locked() {
            UUID userId = UUID.randomUUID();

            User user = User.builder()
                    .email("test@example.com")
                    .nickname("testuser")
                    .password("encoded-password")
                    .build();

            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.login("test@example.com", "pass1234")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("입력값이 null 또는 blank면 validation 예외가 발생한다")
        void login_fail_when_input_invalid() {
            BusinessException nullException = assertThrows(
                    BusinessException.class,
                    () -> authService.login(null, "pass1234")
            );

            BusinessException blankException = assertThrows(
                    BusinessException.class,
                    () -> authService.login("   ", "pass1234")
            );

            assertAll(
                    () -> assertThat(nullException.getErrorCode()).isEqualTo(ErrorCode.AUTH_VALIDATION_FAILED),
                    () -> assertThat(blankException.getErrorCode()).isEqualTo(ErrorCode.AUTH_VALIDATION_FAILED)
            );

            verify(userRepository, never()).findByEmail(anyString());
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTest {

        private User activeUser(UUID userId) {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
            return user;
        }

        @Test
        @DisplayName("유효한 refresh token이면 새 access token을 발급한다")
        void refresh_success() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(14);

            RefreshToken savedToken = RefreshToken.builder()
                    .userId(userId)
                    .token("refresh-token")
                    .expiresAt(expiresAt)
                    .build();

            when(jwtProvider.isValid("refresh-token")).thenReturn(true);
            when(jwtProvider.getTokenType("refresh-token")).thenReturn("refresh");
            when(jwtProvider.getUserId("refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(savedToken));
            when(jwtProvider.generateAccessToken(userId)).thenReturn("new-access-token");

            String result = authService.refresh("refresh-token");

            assertThat(result).isEqualTo("new-access-token");
            verify(jwtProvider).generateAccessToken(userId);
        }

        @Test
        @DisplayName("토큰이 유효하지 않으면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_token_invalid() {
            when(jwtProvider.isValid("invalid-token")).thenReturn(false);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("invalid-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(userRepository, never()).findById(any(UUID.class));
            verify(refreshTokenRepository, never()).findById(any(UUID.class));
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }

        @Test
        @DisplayName("refresh 토큰 타입이 아니면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_token_type_is_not_refresh() {
            when(jwtProvider.isValid("access-token")).thenReturn(true);
            when(jwtProvider.getTokenType("access-token")).thenReturn("access");

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("access-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(userRepository, never()).findById(any(UUID.class));
            verify(refreshTokenRepository, never()).findById(any(UUID.class));
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }

        @Test
        @DisplayName("DB에 저장된 refresh token이 없으면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_saved_token_not_found() {
            UUID userId = UUID.randomUUID();

            when(jwtProvider.isValid("refresh-token")).thenReturn(true);
            when(jwtProvider.getTokenType("refresh-token")).thenReturn("refresh");
            when(jwtProvider.getUserId("refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("refresh-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }

        @Test
        @DisplayName("DB에 저장된 토큰 값과 다르면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_token_not_matched() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(14);

            RefreshToken savedToken = RefreshToken.builder()
                    .userId(userId)
                    .token("different-token")
                    .expiresAt(expiresAt)
                    .build();

            when(jwtProvider.isValid("refresh-token")).thenReturn(true);
            when(jwtProvider.getTokenType("refresh-token")).thenReturn("refresh");
            when(jwtProvider.getUserId("refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(savedToken));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("refresh-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }

        @Test
        @DisplayName("revoked 된 토큰이면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_token_revoked() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(14);

            RefreshToken savedToken = RefreshToken.builder()
                    .userId(userId)
                    .token("refresh-token")
                    .expiresAt(expiresAt)
                    .build();
            ReflectionTestUtils.setField(savedToken, "revoked", true);

            when(jwtProvider.isValid("refresh-token")).thenReturn(true);
            when(jwtProvider.getTokenType("refresh-token")).thenReturn("refresh");
            when(jwtProvider.getUserId("refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(savedToken));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("refresh-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }

        @Test
        @DisplayName("만료된 refresh token이면 AUTH_INVALID_TOKEN 예외가 발생한다")
        void refresh_fail_when_token_expired() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime expiresAt = OffsetDateTime.now().minusMinutes(1);

            RefreshToken savedToken = RefreshToken.builder()
                    .userId(userId)
                    .token("refresh-token")
                    .expiresAt(expiresAt)
                    .build();

            when(jwtProvider.isValid("refresh-token")).thenReturn(true);
            when(jwtProvider.getTokenType("refresh-token")).thenReturn("refresh");
            when(jwtProvider.getUserId("refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
            when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(savedToken));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.refresh("refresh-token")
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
            verify(jwtProvider, never()).generateAccessToken(any(UUID.class));
        }
    }
}
