package com.solv.wefin.domain.group.dto;

import com.solv.wefin.domain.group.entity.GroupInvite;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class GroupInviteInfo {

    private Long codeId;
    private Long groupId;
    private UUID inviteCode;
    private String status;
    private OffsetDateTime expiredAt;

    public static GroupInviteInfo from(GroupInvite invite) {
        return GroupInviteInfo.builder()
                .codeId(invite.getId())
                .groupId(invite.getGroup().getId())
                .inviteCode(invite.getInviteCode())
                .status(invite.getStatus().name())
                .expiredAt(invite.getExpiredAt())
                .build();
    }
}