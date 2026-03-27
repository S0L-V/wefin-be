package com.solv.wefin.web.game.room.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CreateRoomResponse {

    private UUID roomId;
    private String status;
}
