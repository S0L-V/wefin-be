package com.solv.wefin.domain.vote.dto.info;

import com.solv.wefin.domain.vote.entity.VoteStatus;

import java.time.OffsetDateTime;

public record VoteInfo(
        Long voteId,
        String title,
        VoteStatus status,
        int maxSelectCount,
        OffsetDateTime endsAt
) {
}
