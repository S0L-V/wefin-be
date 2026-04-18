package com.solv.wefin.domain.group.dto;

public record MyActiveGroupInfo(
        Long groupId,
        String groupName,
        boolean isHomeGroup
) {
}