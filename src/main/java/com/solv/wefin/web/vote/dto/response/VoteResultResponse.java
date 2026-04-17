package com.solv.wefin.web.vote.dto.response;

import com.solv.wefin.domain.vote.dto.info.VoteOptionResultInfo;
import com.solv.wefin.domain.vote.dto.info.VoteResultInfo;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteResultResponse(
        Long voteId,
        String title,
        String status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        long participantCount,
        List<VoteOptionResultItem> options
) {
    public record VoteOptionResultItem(
            Long optionId,
            String optionText,
            long voteCount,
            double rate,
            boolean selectedByMe
    ) {
        public static VoteOptionResultItem from(VoteOptionResultInfo info) {
            return new VoteOptionResultItem(
                    info.optionId(),
                    info.optionText(),
                    info.voteCount(),
                    info.rate(),
                    info.selectedByMe()
            );
        }
    }

    public static VoteResultResponse from(VoteResultInfo info) {
        return new VoteResultResponse(
                info.voteId(),
                info.title(),
                info.status().name(),
                info.maxSelectCount(),
                info.endsAt(),
                info.closed(),
                info.participantCount(),
                info.options().stream()
                        .map(VoteOptionResultItem::from)
                        .toList()
        );
    }
}
