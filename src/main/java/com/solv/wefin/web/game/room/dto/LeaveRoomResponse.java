package com.solv.wefin.web.game.room.dto;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LeaveRoomResponse {

    private String message;

    public static LeaveRoomResponse success() {
        return new LeaveRoomResponse("퇴장 완료");
    }
}
