package com.solv.wefin.domain.game.room.repository;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {
    boolean existsByUserIdAndStartedAtBetween(UUID userId, LocalDateTime from, LocalDateTime to);
}
