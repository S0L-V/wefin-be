package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.room.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class StartRoomResponse {

    private UUID roomId;
    private RoomStatus status;
    private TurnDetailDto currentTurn;

    public static StartRoomResponse from(UUID roomId, RoomStatus status, TurnDetailDto currentTurn) {
        return new StartRoomResponse(roomId, status, currentTurn);
    }
}