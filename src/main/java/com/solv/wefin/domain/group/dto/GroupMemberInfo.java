package com.solv.wefin.domain.group.dto;

import com.solv.wefin.domain.group.entity.GroupMember;

import java.util.UUID;

public record GroupMemberInfo(
        UUID userId,
        Long groupId,
        String groupName,
        String nickname,
        String role
) {
    public static GroupMemberInfo from(GroupMember groupMember) {
        return new GroupMemberInfo(
                groupMember.getUser().getUserId(),
                groupMember.getGroup().getId(),
                groupMember.getGroup().getName(),
                groupMember.getUser().getNickname(),
                groupMember.getRole().name()
        );
    }
}