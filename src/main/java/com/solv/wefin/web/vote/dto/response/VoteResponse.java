package com.solv.wefin.web.vote.dto.response;

import com.solv.wefin.domain.vote.dto.info.VoteInfo;

import java.time.OffsetDateTime;

public record VoteResponse(
        Long voteId,
        String title,
        String status,
        int maxSelectCount,
        OffsetDateTime endsAt
) {
    public static VoteResponse from(VoteInfo info) {
        return new VoteResponse(
                info.voteId(),
                info.title(),
                info.status().name(),
                info.maxSelectCount(),
                info.endsAt()
        );
    }
}
