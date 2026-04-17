package com.solv.wefin.domain.vote.dto.info;

import com.solv.wefin.domain.vote.entity.VoteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteResultInfo(
        Long voteId,
        String title,
        VoteStatus status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        long participantCount,
        List<VoteOptionResultInfo> options
) {
    public VoteResultInfo {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
