package com.solv.wefin.domain.game.room.repository;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {
    boolean existsByUserIdAndStartedAtBetween(UUID userId, LocalDateTime from, LocalDateTime to);
    //만들어진 방 체크
    boolean existsByGroupIdAndStatusIn(Long groupId, List<String> statuses);

    //중복 방 방지
    boolean existsByUserIdAndStatusIn(UUID userId, List<String> statuses);

    // 목록 조회용
    List<GameRoom> findByGroupId(Long groupId);
    List<GameRoom> findByGroupIdAndStatus(Long groupId, String status);
}
