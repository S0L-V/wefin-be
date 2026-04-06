package com.solv.wefin.domain.group.dto;

import com.solv.wefin.domain.group.entity.GroupInvite;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GroupInviteInfo(
        Long codeId,
        Long groupId,
        UUID inviteCode,
        GroupInvite.InviteStatus status,
        OffsetDateTime expiredAt
) {
    public static GroupInviteInfo from(GroupInvite invite) {
        return new GroupInviteInfo(
                invite.getId(),
                invite.getGroup().getId(),
                invite.getInviteCode(),
                invite.getStatus(),
                invite.getExpiredAt()
        );
    }
}