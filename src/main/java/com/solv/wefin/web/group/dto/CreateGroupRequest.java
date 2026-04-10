package com.solv.wefin.web.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "그룹 이름은 필수입니다.")
        @Size(max = 100, message = "그룹 이름은 100자 이하여야 합니다.")
        String name
) {
}