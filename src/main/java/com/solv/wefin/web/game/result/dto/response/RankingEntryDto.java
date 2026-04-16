package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RankingEntryDto {

    private int rank;
    private String userName;
    private BigDecimal seedMoney;
    private BigDecimal finalAsset;
    private BigDecimal profitRate;
    private int totalTrades;

    public static RankingEntryDto from(GameResultInfo.RankingEntry entry) {
        return new RankingEntryDto(
                entry.rank(),
                entry.userName(),
                entry.seedMoney(),
                entry.finalAsset(),
                entry.profitRate(),
                entry.totalTrades());
    }
}
