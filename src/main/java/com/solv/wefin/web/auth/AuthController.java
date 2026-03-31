package com.solv.wefin.web.auth;

import com.solv.wefin.domain.auth.dto.SignupCommand;
import com.solv.wefin.domain.auth.dto.SignupResult;
import com.solv.wefin.domain.auth.service.AuthService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.auth.dto.SignupRequest;
import com.solv.wefin.web.auth.dto.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        SignupResult result = authService.signup(
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
}