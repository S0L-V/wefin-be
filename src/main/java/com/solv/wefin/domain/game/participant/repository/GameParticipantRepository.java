package com.solv.wefin.domain.game.participant.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, UUID> {

    // 참가자 수 카운트
    int countByGameRoomAndStatus(GameRoom gameRoom, ParticipantStatus status);

    List<GameParticipant> findByGameRoomOrderByJoinedAtAsc(GameRoom gameRoom);



}
