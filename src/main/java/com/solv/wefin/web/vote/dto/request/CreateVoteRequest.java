package com.solv.wefin.web.vote.dto.request;

import com.solv.wefin.domain.vote.dto.command.CreateVoteCommand;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateVoteRequest(
        @NotNull Long groupId,
        @NotBlank String title,
        @NotEmpty List<@NotBlank String> options,
        @Min(1) int maxSelectCount,
        @NotNull @Min(1) Long durationHours
        ) {
    public CreateVoteCommand toCommand() {
        return new CreateVoteCommand(
                groupId,
                title,
                options,
                maxSelectCount,
                durationHours
        );
    }
}
