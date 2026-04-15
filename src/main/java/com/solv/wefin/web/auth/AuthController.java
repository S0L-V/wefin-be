package com.solv.wefin.web.auth;

import com.solv.wefin.domain.auth.dto.LoginInfo;
import com.solv.wefin.domain.auth.dto.SignupCommand;
import com.solv.wefin.domain.auth.dto.SignupInfo;
import com.solv.wefin.domain.auth.service.AuthService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.auth.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        SignupInfo result = authService.signup(
                new SignupCommand(
                        request.getEmail(),
                        request.getNickname(),
                        request.getPassword()
                )
        );

        SignupResponse response = SignupResponse.builder()
                .userId(result.userId())
                .email(result.email())
                .nickname(result.nickname())
                .build();

        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginInfo result = authService.login(
                request.email(),
                request.password()
        );

        LoginResponse response = LoginResponse.builder()
                .accessToken(result.accessToken())
                .refreshToken(result.refreshToken())
                .userId(result.userId())
                .nickname(result.nickname())
                .build();

        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        String newAccessToken = authService.refresh(request.refreshToken());

        RefreshTokenResponse response = new RefreshTokenResponse(newAccessToken);

        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            @RequestBody @Valid LogoutRequest request
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        authService.logout(userId, request.refreshToken());
        return ApiResponse.success(null);
    }
}