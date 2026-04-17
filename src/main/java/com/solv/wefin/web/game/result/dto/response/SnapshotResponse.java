package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.snapshot.dto.SnapshotInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class SnapshotResponse {

    private Integer turnNumber;
    private LocalDate turnDate;
    private BigDecimal totalAsset;
    private BigDecimal cash;
    private BigDecimal stockValue;
    private BigDecimal profitRate;

    public static SnapshotResponse from(SnapshotInfo info) {
        return new SnapshotResponse(
                info.turnNumber(),
                info.turnDate(),
                info.totalAsset(),
                info.cash(),
                info.stockValue(),
                info.profitRate());
    }
}
