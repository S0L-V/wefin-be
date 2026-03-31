package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class JoinRoomResponse {

    private UUID participantId;
    private UUID roomId;
    private RoomStatus roomStatus;

    public static JoinRoomResponse from(GameParticipant participant) {
        return new JoinRoomResponse(
                participant.getParticipantId(),
                participant.getGameRoom().getRoomId(),
                participant.getGameRoom().getStatus()
        );
    }
}