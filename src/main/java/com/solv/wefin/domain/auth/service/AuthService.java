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
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String UK_USERS_EMAIL = "uk_users_email";
    private static final String UK_USERS_NICKNAME = "uk_users_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupInfo signup(SignupCommand command) {
        String email = command.email();
        String nickname = command.nickname();
        String password = command.password();

        if (email == null || nickname == null || password == null) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        nickname = nickname.trim();

        if (email.isBlank() || nickname.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        if (!password.equals(password.trim())) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
        }

        try {
            User user = User.builder()
                    .email(email)
                    .nickname(nickname)
                    .password(passwordEncoder.encode(password))
                    .build();

            User savedUser = userRepository.save(user);

            Group homeGroup = groupService.createDefaultGroup(savedUser);
            savedUser.setHomeGroup(homeGroup);
            userRepository.save(savedUser);

            return new SignupInfo(
                    savedUser.getUserId(),
                    savedUser.getEmail(),
                    savedUser.getNickname()
            );

        } catch (DataIntegrityViolationException e) {
            throw mapConstraintViolation(e);
        }
    }

    @Transactional
    public LoginInfo login(String email, String password) {
        if (email == null || password == null) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        email = email.trim().toLowerCase(Locale.ROOT);

        if (email.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        if (!password.equals(password.trim())) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String refreshTokenValue = jwtProvider.generateRefreshToken(user.getUserId());
        OffsetDateTime refreshTokenExpiresAt = jwtProvider.getExpiration(refreshTokenValue);

        RefreshToken refreshToken = refreshTokenRepository.findById(user.getUserId())
                .map(saved -> {
                    saved.update(refreshTokenValue, refreshTokenExpiresAt);
                    return saved;
                })
                .orElseGet(() -> RefreshToken.builder()
                        .userId(user.getUserId())
                        .token(refreshTokenValue)
                        .expiresAt(refreshTokenExpiresAt)
                        .build());

        refreshTokenRepository.save(refreshToken);

        return new LoginInfo(
                user.getUserId(),
                user.getNickname(),
                accessToken,
                refreshTokenValue
        );
    }

    @Transactional
    public String refresh(String refreshToken) {

        // 토큰 유효성 검증
        if (!jwtProvider.isValid(refreshToken) || !"refresh".equals(jwtProvider.getTokenType(refreshToken))) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        UUID userId = jwtProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        RefreshToken savedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        // 토큰 일치 여부 확인
        if (!savedToken.getToken().equals(refreshToken) || savedToken.isRevoked()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 만료 체크
        if (!savedToken.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 새 access token 발급
        return jwtProvider.generateAccessToken(userId);
    }

    private BusinessException mapConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();

                if (UK_USERS_EMAIL.equals(constraint)) {
                    return new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
                }
                if (UK_USERS_NICKNAME.equals(constraint)) {
                    return new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
                }
            }
            cause = cause.getCause();
        }

        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }
}