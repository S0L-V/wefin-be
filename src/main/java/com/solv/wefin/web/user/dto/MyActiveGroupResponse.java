package com.solv.wefin.web.user.dto;

import com.solv.wefin.domain.group.dto.MyActiveGroupInfo;

public record MyActiveGroupResponse(
        Long groupId,
        String groupName,
        boolean isHomeGroup
) {
    public static MyActiveGroupResponse from(MyActiveGroupInfo info) {
        return new MyActiveGroupResponse(
                info.groupId(),
                info.groupName(),
                info.isHomeGroup()
        );
    }
}