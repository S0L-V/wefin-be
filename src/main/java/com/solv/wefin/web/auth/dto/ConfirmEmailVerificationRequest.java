package com.solv.wefin.web.auth.dto;

import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmEmailVerificationRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    private String email;

    @NotBlank(message = "인증코드는 필수입니다.")
    private String code;

    @NotNull(message = "인증 목적은 필수입니다.")
    private VerificationPurpose purpose;
}
