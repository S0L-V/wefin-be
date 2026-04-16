package com.solv.wefin.domain.game.result.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 게임 결과 조회 응답용 Domain DTO.
 * 본인이 FINISHED 상태일 때, 현 시점 game_result 기준 순위와 참가자별 결과를 묶어서 반환한다.
 * 방이 아직 IN_PROGRESS여도 본인이 FINISHED이기만 하면 조회 가능하며,
 * 이 경우 아직 종료하지 않은 참가자는 결과에 포함되지 않는다.
 */
public record GameResultInfo(
        UUID roomId,
        LocalDate startDate,
        LocalDate endDate,
        int totalTurns,
        List<RankingEntry> rankings
) {
    public record RankingEntry(
            int rank,
            String userName,
            BigDecimal seedMoney,
            BigDecimal finalAsset,
            BigDecimal profitRate,
            int totalTrades
    ) {}
}
