package com.solv.wefin.web.game.turn.dto.response;

import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CurrentTurnResponse(
        UUID turnId,
        int turnNumber,
        LocalDate turnDate,
        TurnStatus status,
        UUID briefingId
) {
    public static CurrentTurnResponse from(GameTurn turn) {
        return new CurrentTurnResponse(
                turn.getTurnId(),
                turn.getTurnNumber(),
                turn.getTurnDate(),
                turn.getStatus(),
                turn.getBriefingId()
        );
    }
}
