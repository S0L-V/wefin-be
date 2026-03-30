package com.solv.wefin.domain.auth.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.auth.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(String email, String nickname, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
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
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);

            if (root instanceof ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();

                if ("uk_users_email".equals(constraint)) {
                    throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
                }
            }

            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }
}