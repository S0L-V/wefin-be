package com.solv.wefin.web.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class WithdrawRequest {

    @NotBlank
    private String password;
}