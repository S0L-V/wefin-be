package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.GroupMemberInfo;

public record CreateGroupResponse(
        Long groupId,
        String groupName,
        String role
) {

    public static CreateGroupResponse from(GroupMemberInfo info) {
        return new CreateGroupResponse(
                info.groupId(),
                info.groupName(),
                info.role()
        );
    }
}