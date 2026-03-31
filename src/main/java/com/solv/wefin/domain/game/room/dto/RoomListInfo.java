package com.solv.wefin.domain.game.room.dto;

import com.solv.wefin.domain.game.room.entity.GameRoom;

public record RoomListInfo(GameRoom room, int playerCount) {
}
