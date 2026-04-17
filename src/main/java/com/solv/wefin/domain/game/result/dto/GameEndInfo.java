package com.solv.wefin.domain.game.result.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GameEndInfo(
        UUID participantId,
        BigDecimal finalAsset,
        BigDecimal profitRate,
        int totalTrades,
        boolean roomFinished
) {
}
