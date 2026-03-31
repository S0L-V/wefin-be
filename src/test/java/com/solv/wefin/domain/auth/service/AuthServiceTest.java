package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.dto.SignupCommand;
import com.solv.wefin.domain.auth.dto.SignupResult;
import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.service.GroupService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GroupService groupService;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup")
    class SignupTest {

        @Test
        @DisplayName("회원가입에 성공한다")
        void signup_success() {
            // given
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

            // when
            SignupResult response = authService.signup(
                    new SignupCommand(rawEmail, rawNickname, rawPassword)
            );

            // then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            verify(groupService).createDefaultGroup(savedUser);

            User user = captor.getValue();

            assertAll(
                    () -> assertThat(user.getEmail()).isEqualTo("test@example.com"),
                    () -> assertThat(user.getNickname()).isEqualTo("testuser"),
                    () -> assertThat(user.getPassword()).isEqualTo("encoded-password"),
                    () -> assertThat(response.userId()).isEqualTo(userId),
                    () -> assertThat(response.email()).isEqualTo("test@example.com"),
                    () -> assertThat(response.nickname()).isEqualTo("testuser")
            );
        }

        @Test
        @DisplayName("이메일이 중복되면 예외가 발생한다")
        void signup_fail_when_email_duplicated() {
            // given
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
            verify(userRepository, never()).existsByNickname(anyString());
            verify(userRepository, never()).save(any(User.class));
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외가 발생한다")
        void signup_fail_when_nickname_duplicated() {
            // given
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(true);

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            // then
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
            // given
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");

            ConstraintViolationException cause =
                    new ConstraintViolationException("constraint violated", new SQLException(), "uk_users_email");

            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("db error", cause));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }

        @Test
        @DisplayName("DB 닉네임 unique 제약 위반 시 닉네임 중복 예외로 변환한다")
        void signup_fail_when_nickname_constraint_violated() {
            // given
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("nickname")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");

            ConstraintViolationException cause =
                    new ConstraintViolationException("constraint violated", new SQLException(), "uk_users_nickname");

            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("db error", cause));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> authService.signup(
                            new SignupCommand("test@example.com", "nickname", "pass1234")
                    )
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_NICKNAME_DUPLICATED);
            verify(groupService, never()).createDefaultGroup(any(User.class));
        }
    }
}