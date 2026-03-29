package com.solv.wefin.web.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SignupResponse {
    private UUID userId;
    private String email;
    private String nickname;
}
