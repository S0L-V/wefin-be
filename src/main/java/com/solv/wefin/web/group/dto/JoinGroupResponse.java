package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.GroupMemberInfo;

public record JoinGroupResponse(
        Long groupId,
        String groupName,
        String role,
        String status
) {
    public static JoinGroupResponse from(GroupMemberInfo info) {
        return new JoinGroupResponse(
                info.groupId(),
                info.groupName(),
                info.role(),
                "ACTIVE"
        );
    }
}