package com.solv.wefin.domain.game.participant.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, UUID> {

    int countByGameRoomAndStatus(GameRoom gameRoom, ParticipantStatus status);

}
