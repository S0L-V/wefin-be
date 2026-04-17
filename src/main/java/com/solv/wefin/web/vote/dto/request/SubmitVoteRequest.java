package com.solv.wefin.web.vote.dto.request;

import com.solv.wefin.domain.vote.dto.command.SubmitVoteCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitVoteRequest(
        @NotEmpty List<@NotNull Long> optionIds
) {
    public SubmitVoteCommand toCommand() {
        return new SubmitVoteCommand(optionIds);
    }
}
