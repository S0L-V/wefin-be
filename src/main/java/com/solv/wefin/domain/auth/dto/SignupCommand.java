package com.solv.wefin.domain.auth.dto;

public record SignupCommand(
        String email,
        String nickname,
        String password
) {
}
