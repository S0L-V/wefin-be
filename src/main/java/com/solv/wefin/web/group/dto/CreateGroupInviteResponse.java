package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.GroupInviteInfo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateGroupInviteResponse(
        Long codeId,
        Long groupId,
        UUID inviteCode,
        String status,
        OffsetDateTime expiredAt
) {
    public static CreateGroupInviteResponse from(GroupInviteInfo info) {
        return new CreateGroupInviteResponse(
                info.codeId(),
                info.groupId(),
                info.inviteCode(),
                info.status().name(),
                info.expiredAt()
        );
    }
}