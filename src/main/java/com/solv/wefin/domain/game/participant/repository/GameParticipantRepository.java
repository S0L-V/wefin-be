package com.solv.wefin.domain.game.participant.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, UUID> {

    // 참가자 수 카운트
    int countByGameRoomAndStatus(GameRoom gameRoom, ParticipantStatus status);

    List<GameParticipant> findByGameRoomOrderByJoinedAtAsc(GameRoom gameRoom);

    //게임 참가 확인 - 중복 방지
    Boolean existsByGameRoomAndUserId(GameRoom gameRoom, UUID userId);

    // 유저 참가 이력 조회 - 재참가 때 사용
    Optional<GameParticipant> findByGameRoomAndUserId(GameRoom gameRoom, UUID userId);

    //방장 위임 - 참가자 중 랜덤 선택
    List<GameParticipant> findByGameRoomAndStatus(GameRoom gameRoom, ParticipantStatus status);

    // 매수/매도 시 참가자 잔액 비관적 락 (SELECT FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM GameParticipant p WHERE p.gameRoom = :gameRoom AND p.userId = :userId")
    Optional<GameParticipant> findByGameRoomAndUserIdForUpdate(GameRoom gameRoom, UUID userId);
}
