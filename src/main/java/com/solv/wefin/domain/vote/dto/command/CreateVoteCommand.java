package com.solv.wefin.domain.vote.dto.command;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateVoteCommand(
        Long groupId,
        String title,
        List<String> options,
        int maxSelectCount,
        Long durationHours
) {
    public CreateVoteCommand {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
