package com.solv.wefin.domain.game.snapshot.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RankingInfo(
        int rank,
        UUID userId,
        String userName,
        BigDecimal totalAsset,
        BigDecimal profitRate
) {
}
