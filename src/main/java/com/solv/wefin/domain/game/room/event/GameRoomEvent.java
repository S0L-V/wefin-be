package com.solv.wefin.domain.game.room.event;

import java.util.UUID;

public record GameRoomEvent(UUID roomId, EventType type) {
    public enum EventType {
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        PARTICIPANT_FINISHED,
        GAME_STARTED
    }
}
