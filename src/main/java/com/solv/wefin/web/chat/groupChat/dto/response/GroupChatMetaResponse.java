package com.solv.wefin.web.chat.groupChat.dto.response;

import com.solv.wefin.domain.group.entity.Group;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupChatMetaResponse {
    private Long groupId;
    private String groupName;

    public static GroupChatMetaResponse from(Group group) {
        return GroupChatMetaResponse.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .build();
    }
}
