package com.solv.wefin.domain.game.snapshot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SnapshotInfo(
        int turnNumber,
        LocalDate turnDate,
        BigDecimal totalAsset,
        BigDecimal cash,
        BigDecimal stockValue,
        BigDecimal profitRate
) {
}
