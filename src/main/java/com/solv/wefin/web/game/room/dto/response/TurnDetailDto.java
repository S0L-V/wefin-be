package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.turn.entity.GameTurn;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TurnDetailDto {

    private UUID turnId;
    private Integer turnNumber;
    private LocalDate turnDate;

    public static TurnDetailDto from(GameTurn gameTurn) {
        return new TurnDetailDto(
                gameTurn.getTurnId(),
                gameTurn.getTurnNumber(),
                gameTurn.getTurnDate()
        );
    }
}
