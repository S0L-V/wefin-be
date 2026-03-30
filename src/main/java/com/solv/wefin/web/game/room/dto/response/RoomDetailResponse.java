package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RoomDetailResponse {

    private UUID roomId;
    private UUID hostId;
    private Long seed;
    private Integer periodMonths;
    private Integer moveDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private RoomStatus status;
    private List<ParticipantDetailDto> participants;

    public static RoomDetailResponse from (GameRoom room, List<ParticipantDetailDto> participants) {
        return new RoomDetailResponse(
                room.getRoomId(),
                room.getUserId(),
                room.getSeed(),
                room.getPeriodMonth(),
                room.getMoveDays(),
                room.getStartDate(),
                room.getEndDate(),
                room.getStatus(),
                participants
        );
    }
}
