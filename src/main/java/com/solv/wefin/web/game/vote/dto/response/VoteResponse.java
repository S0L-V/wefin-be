package com.solv.wefin.web.game.vote.dto.response;

import com.solv.wefin.domain.game.vote.VoteSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoteResponse {

    private boolean voted;
    private long agreeCount;
    private long disagreeCount;
    private int totalCount;

    public static VoteResponse from(VoteSession session) {
        return new VoteResponse(
                true,
                session.getAgreeCount(),
                session.getDisagreeCount(),
                session.getTotalCount()
        );
    }
}
