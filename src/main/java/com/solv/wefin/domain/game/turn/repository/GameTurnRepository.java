package com.solv.wefin.domain.game.turn.repository;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameTurnRepository extends JpaRepository<GameTurn, UUID> {

    Optional<GameTurn> findByGameRoomAndStatus(GameRoom gameRoom, TurnStatus status);

    Optional<GameTurn> findFirstByGameRoomAndStatusOrderByTurnNumberDesc(GameRoom gameRoom, TurnStatus status);

    int countByGameRoomAndStatus(GameRoom gameRoom, TurnStatus status);
}
