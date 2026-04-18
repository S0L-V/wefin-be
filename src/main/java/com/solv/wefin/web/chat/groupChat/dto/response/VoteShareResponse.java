package com.solv.wefin.web.chat.groupChat.dto.response;

import com.solv.wefin.domain.chat.groupChat.dto.info.VoteShareInfo;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteShareResponse(
        Long voteId,
        String title,
        String status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        List<VoteShareOptionResponse> options
) {
    public static VoteShareResponse from(VoteShareInfo info) {
        return new VoteShareResponse(
                info.voteId(),
                info.title(),
                info.status(),
                info.maxSelectCount(),
                info.endsAt(),
                info.closed(),
                info.options().stream()
                        .map(VoteShareOptionResponse::from)
                        .toList()
        );
    }
}
