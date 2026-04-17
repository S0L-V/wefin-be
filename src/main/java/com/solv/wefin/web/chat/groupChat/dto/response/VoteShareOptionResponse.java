package com.solv.wefin.web.chat.groupChat.dto.response;

import com.solv.wefin.domain.chat.groupChat.dto.info.VoteShareOptionInfo;

public record VoteShareOptionResponse(
        Long optionId,
        String optionText
) {
    public static VoteShareOptionResponse from(VoteShareOptionInfo info) {
        return new VoteShareOptionResponse(
                info.optionId(),
                info.optionText()
        );
    }
}
