package com.solv.wefin.web.game.turn.dto.response;

import com.solv.wefin.domain.game.turn.entity.GameTurn;

import java.time.LocalDate;
import java.util.UUID;

public record TurnAdvanceResponse(
        boolean gameFinished,
        UUID turnId,
        Integer turnNumber,
        LocalDate turnDate,
        UUID briefingId
) {
    public static TurnAdvanceResponse from(GameTurn nextTurn) {
        return new TurnAdvanceResponse(
                false,
                nextTurn.getTurnId(),
                nextTurn.getTurnNumber(),
                nextTurn.getTurnDate(),
                nextTurn.getBriefingId()
        );
    }

    public static TurnAdvanceResponse finished() {
        return new TurnAdvanceResponse(true, null, null, null, null);
    }
}
