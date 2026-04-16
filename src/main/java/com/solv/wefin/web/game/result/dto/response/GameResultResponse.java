package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GameResultResponse {

    private UUID roomId;
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalTurns;
    private List<RankingEntryDto> rankings;

    public static GameResultResponse from(GameResultInfo info) {
        List<RankingEntryDto> rankings = info.rankings().stream()
                .map(RankingEntryDto::from)
                .toList();

        return new GameResultResponse(
                info.roomId(),
                info.startDate(),
                info.endDate(),
                info.totalTurns(),
                rankings);
    }
}
