package com.solv.wefin.web.user.dto;

import com.solv.wefin.domain.user.dto.MyPageInfo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MyPageResponse(
        UUID userId,
        String email,
        String nickname,
        OffsetDateTime createdAt
) {
    public static MyPageResponse from(MyPageInfo info) {
        return new MyPageResponse(
                info.userId(),
                info.email(),
                info.nickname(),
                info.createdAt()
        );
    }
}