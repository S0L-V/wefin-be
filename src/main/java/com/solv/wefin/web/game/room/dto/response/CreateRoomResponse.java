package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CreateRoomResponse {

    private UUID roomId;
    private String status;
    public static CreateRoomResponse from(GameRoom room) {
        return new CreateRoomResponse(room.getRoomId(), room.getStatus());
    }
}
