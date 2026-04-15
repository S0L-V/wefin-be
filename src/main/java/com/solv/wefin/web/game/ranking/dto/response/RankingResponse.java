package com.solv.wefin.web.game.ranking.dto.response;

import com.solv.wefin.domain.game.snapshot.dto.RankingInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RankingResponse {

    private int rank;
    private UUID userId;
    private String userName;
    private BigDecimal totalAsset;
    private BigDecimal profitRate;

    public static RankingResponse from(RankingInfo info) {
        return new RankingResponse(
                info.rank(),
                info.userId(),
                info.userName(),
                info.totalAsset(),
                info.profitRate()
        );
    }
}
