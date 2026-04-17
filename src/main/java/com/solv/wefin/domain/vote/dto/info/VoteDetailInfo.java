package com.solv.wefin.domain.vote.dto.info;

import com.solv.wefin.domain.vote.entity.VoteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteDetailInfo(
        Long voteId,
        String title,
        VoteStatus status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        List<VoteOptionInfo> options,
        List<Long> myOptionIds
) {
    public VoteDetailInfo {
        options = options == null ? List.of() : List.copyOf(options);
        myOptionIds = myOptionIds == null ? List.of() : List.copyOf(myOptionIds);
    }
}
