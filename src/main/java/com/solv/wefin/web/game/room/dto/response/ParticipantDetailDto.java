package com.solv.wefin.web.game.room.dto.response;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ParticipantDetailDto {

    private UUID participantId;
    private UUID userId;
    private String userName;
    private Boolean isLeader;
    private ParticipantStatus status;
    private OffsetDateTime joinedAt;

    public static ParticipantDetailDto from (GameParticipant participant, String userName) {
        return new ParticipantDetailDto(
                participant.getParticipantId(),
                participant.getUserId(),
                userName,
                participant.getIsLeader(),
                participant.getStatus(),
                participant.getJoinedAt()

        );
    }
}
