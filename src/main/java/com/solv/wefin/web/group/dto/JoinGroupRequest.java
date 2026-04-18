package com.solv.wefin.web.group.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record JoinGroupRequest(
        @NotNull UUID inviteCode
) {}