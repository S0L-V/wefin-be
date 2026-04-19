package com.solv.wefin.domain.game.room.repository;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {
    // 방장 1일 횟수 제한
    long countByUserIdAndStartedAtBetween(UUID userId, OffsetDateTime from, OffsetDateTime to);

    //만들어진 방 체크
    boolean existsByGroupIdAndStatusIn(Long groupId, List<RoomStatus> statuses);

    //중복 방 방지
    boolean existsByUserIdAndStatusIn(UUID userId, List<RoomStatus> statuses);

    // 목록 조회용바
    List<GameRoom> findByGroupIdAndStatusIn(Long groupId, List<RoomStatus> statuses);
    @Query("SELECT r FROM GameRoom r JOIN GameParticipant p ON p.gameRoom = r "
            + "WHERE r.groupId = :groupId AND r.status = 'FINISHED' AND p.userId = :userId "
            + "ORDER BY r.createdAt DESC")
    List<GameRoom> findFinishedRoomsByGroupIdAndUserId(
            @Param("groupId") Long groupId, @Param("userId") UUID userId);

    //게임방 조회, 비관적 락 -> 동시성 제어
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM GameRoom r WHERE r.roomId = :roomId")
    Optional<GameRoom> findByIdForUpdate(@Param("roomId") UUID roomId);


}
