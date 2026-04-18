package com.solv.wefin.domain.game.room.dto;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.turn.entity.GameTurn;

public record StartRoomInfo(GameRoom room, GameTurn firstTurn) {
}
