package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.LeaveGroupInfo;

public record LeaveGroupResponse(
        Long leftGroupId,
        Long currentGroupId
) {
    public static LeaveGroupResponse from(LeaveGroupInfo info) {
        return new LeaveGroupResponse(
                info.leftGroupId(),
                info.currentGroupId()
        );
    }
}