package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RoomListResponse {

    private UUID roomId;
    private UUID hostUserId;
    private Long seedMoney;
    private Integer periodMonths;
    private Integer moveDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private int currentPlayers;
    private LocalDateTime createdAt;

    public static RoomListResponse from(GameRoom room, int currentPlayers) {
        return new RoomListResponse(
                room.getRoomId(),
                room.getUserId(),
                room.getSeed(),
                room.getPeriodMonth(),
                room.getMoveDays(),
                room.getStartDate(),
                room.getEndDate(),
                room.getStatus(),
                currentPlayers,
                room.getCreatedAt()
        );
    }
}

