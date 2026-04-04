package com.solv.wefin.domain.game.room.event;

import jdk.jfr.Event;
import jdk.jfr.EventType;

import java.util.UUID;

public record GameRoomEvent(UUID roomId, EventType type) {
    public enum EventType {
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        GAME_STARTED
    }
}
