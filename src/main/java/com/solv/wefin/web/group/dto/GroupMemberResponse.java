package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.GroupMemberInfo;

import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String nickname,
        String role
) {
    public static GroupMemberResponse from(GroupMemberInfo info) {
        return new GroupMemberResponse(
                info.getUserId(),
                info.getNickname(),
                info.getRole()
        );
    }
}