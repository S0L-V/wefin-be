package com.solv.wefin.domain.game.result.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    List<GameResult> findByGameRoomOrderByFinalRankAsc(GameRoom gameRoom);

    List<GameResult> findByGameRoomOrderByFinalAssetDescCreatedAtAsc(GameRoom gameRoom);

    boolean existsByGameRoomAndParticipant(GameRoom gameRoom, GameParticipant participant);
}
