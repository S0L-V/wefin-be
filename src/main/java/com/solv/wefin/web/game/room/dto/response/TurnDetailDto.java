package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.turn.entity.GameTurn;


import java.time.LocalDate;
import java.util.UUID;


public record TurnDetailDto(UUID turnId, Integer turnNumber, LocalDate turnDate) {

    public static TurnDetailDto from(GameTurn gameTurn) {
        return new TurnDetailDto(
                gameTurn.getTurnId(),
                gameTurn.getTurnNumber(),
                gameTurn.getTurnDate()
        );
    }
}
