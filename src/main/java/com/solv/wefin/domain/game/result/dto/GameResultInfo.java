package com.solv.wefin.domain.game.result.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 게임 결과 조회 응답용 Domain DTO.
 * 본인이 FINISHED 상태이면 호출 가능. 방이 아직 IN_PROGRESS여도 호출은 OK이며,
 * 이 경우 roomFinished=false + rankings는 빈 배열이 반환된다
 * (rankings는 방 전체 종료 후에만 의미 있음).
 */
public record GameResultInfo(
        UUID roomId,
        LocalDate startDate,
        LocalDate endDate,
        boolean roomFinished,
        List<RankingEntry> rankings
) {
    public record RankingEntry(
            int rank,
            String userName,
            BigDecimal finalAsset,
            boolean isMine
    ) {}
}
