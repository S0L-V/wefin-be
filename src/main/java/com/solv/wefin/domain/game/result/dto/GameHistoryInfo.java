package com.solv.wefin.domain.game.result.dto;

import com.solv.wefin.domain.game.room.entity.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 과거 게임 이력 조회용 Domain DTO.
 * GameResult + GameRoom 에서 필요한 필드를 추출.
 * finalRank: 방 FINISHED 시 실제 순위, 방 IN_PROGRESS 시 null (DB의 0을 null로 매핑).
 */
public record GameHistoryInfo(
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
) {}
