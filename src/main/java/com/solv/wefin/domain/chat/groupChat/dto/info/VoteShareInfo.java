package com.solv.wefin.domain.chat.groupChat.dto.info;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteShareInfo(
        Long voteId,
        String title,
        String status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        List<VoteShareOptionInfo> options
) {
}
