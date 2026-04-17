package com.solv.wefin.domain.vote.dto.command;

import java.util.List;

public record SubmitVoteCommand(
        List<Long> optionIds
) {
    public SubmitVoteCommand {
        optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
    }
}
