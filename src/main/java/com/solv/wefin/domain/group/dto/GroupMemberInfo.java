package com.solv.wefin.domain.group.dto;

import com.solv.wefin.domain.group.entity.GroupMember;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class GroupMemberInfo {
    private UUID userId;
    private String nickname;
    private String role;

    public static GroupMemberInfo from(GroupMember groupMember) {
        return GroupMemberInfo.builder()
                .userId(groupMember.getUser().getUserId())
                .nickname(groupMember.getUser().getNickname())
                .role(groupMember.getRole().name())
                .build();
    }
}