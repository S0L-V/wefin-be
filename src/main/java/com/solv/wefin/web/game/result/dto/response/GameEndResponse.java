package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GameEndResponse {

    private UUID participantId;
    private BigDecimal finalAsset;
    private BigDecimal profitRate;
    private int totalTrades;
    private boolean roomFinished;

    public static GameEndResponse from(GameEndInfo info) {
        return new GameEndResponse(
                info.participantId(),
                info.finalAsset(),
                info.profitRate(),
                info.totalTrades(),
                info.roomFinished());
    }
}
