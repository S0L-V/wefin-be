package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.result.dto.GameHistoryInfo;
import com.solv.wefin.domain.game.room.entity.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GameHistoryResponse(
        UUID roomId,
        RoomStatus roomStatus,
        BigDecimal seedMoney,
        int periodMonths,
        int moveDays,
        LocalDate startDate,
        LocalDate endDate,
        int participantCount,
        BigDecimal finalAsset,
        BigDecimal profitRate,
        Integer finalRank,
        int totalTrades,
        OffsetDateTime finishedAt
) {
    public static GameHistoryResponse from(GameHistoryInfo info) {
        return new GameHistoryResponse(
                info.roomId(),
                info.roomStatus(),
                info.seedMoney(),
                info.periodMonths(),
                info.moveDays(),
                info.startDate(),
                info.endDate(),
                info.participantCount(),
                info.finalAsset(),
                info.profitRate(),
                info.finalRank(),
                info.totalTrades(),
                info.finishedAt());
    }
}
