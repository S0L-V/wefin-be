package com.solv.wefin.web.vote.dto.response;

import com.solv.wefin.domain.vote.dto.info.VoteDetailInfo;
import com.solv.wefin.domain.vote.dto.info.VoteOptionInfo;

import java.time.OffsetDateTime;
import java.util.List;

public record VoteDetailResponse(
        Long voteId,
        String title,
        String status,
        int maxSelectCount,
        OffsetDateTime endsAt,
        boolean closed,
        List<VoteOptionItem> options,
        List<Long> myOptionIds
) {
    public record VoteOptionItem(
            Long optionId,
            String optionText
    ) {
        public static VoteOptionItem from(VoteOptionInfo info) {
            return new VoteOptionItem(info.optionId(), info.optionText());
        }
    }

    public static VoteDetailResponse from(VoteDetailInfo info) {
        return new VoteDetailResponse(
                info.voteId(),
                info.title(),
                info.status().name(),
                info.maxSelectCount(),
                info.endsAt(),
                info.closed(),
                info.options().stream()
                        .map(VoteOptionItem::from)
                        .toList(),
                info.myOptionIds()
        );
    }
}
