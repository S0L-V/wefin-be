package com.solv.wefin.domain.game.room.dto;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.room.entity.GameRoom;

import java.util.List;

public record RoomDetailInfo(GameRoom room, List<GameParticipant> participants) {
}
