package com.solv.wefin.web.group.dto;

import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class GroupMemberResponse {
    private UUID userId;
    private String nickname;
    private String role;

    public static GroupMemberResponse from(GroupMemberInfo info) {
        return GroupMemberResponse.builder()
                .userId(info.getUserId())
                .nickname(info.getNickname())
                .role(info.getRole())
                .build();
    }
}