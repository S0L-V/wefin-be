package com.solv.wefin.web.interest.dto;

import com.solv.wefin.domain.interest.dto.InterestInfo;

/**
 * 관심사 조회 응답 (SECTOR/TOPIC)
 */
public record InterestResponse(String code, String name) {
    public static InterestResponse from(InterestInfo info) {
        return new InterestResponse(info.code(), info.name());
    }
}
