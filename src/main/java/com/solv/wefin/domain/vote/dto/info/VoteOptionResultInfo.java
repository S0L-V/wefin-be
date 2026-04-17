package com.solv.wefin.domain.vote.dto.info;

public record VoteOptionResultInfo(
        Long optionId,
        String optionText,
        long voteCount,
        double rate,
        boolean selectedByMe
) {
}
