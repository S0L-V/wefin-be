package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.auth.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String UK_USERS_EMAIL = "uk_users_email";
    private static final String UK_USERS_NICKNAME = "uk_users_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(String email, String nickname, String password) {
        // null 처리
        if (email == null || nickname == null || password == null) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        // 입력값 정규화
        email = email.trim().toLowerCase(Locale.ROOT);
        nickname = nickname.trim();

        // 빈 값 처리
        if (email.isBlank() || nickname.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        // 비밀번호 앞뒤 공백 비허용
        if (!password.equals(password.trim())) {
            throw new BusinessException(ErrorCode.AUTH_VALIDATION_FAILED);
        }

        // 중복 처리
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

            return SignupResponse.builder()
                    .userId(savedUser.getUserId())
                    .email(savedUser.getEmail())
                    .nickname(savedUser.getNickname())
                    .build();

        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getCause();

            if (cause instanceof ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();

                if (UK_USERS_EMAIL.equals(constraint)) {
                    throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
                }
                if (UK_USERS_NICKNAME.equals(constraint)) {
                    throw new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
                }
            }

            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }
}