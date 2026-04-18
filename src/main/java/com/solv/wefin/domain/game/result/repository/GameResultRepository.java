package com.solv.wefin.domain.game.result.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    List<GameResult> findByGameRoomOrderByFinalRankAsc(GameRoom gameRoom);

    List<GameResult> findByGameRoomOrderByFinalAssetDescCreatedAtAsc(GameRoom gameRoom);

    boolean existsByGameRoomAndParticipant(GameRoom gameRoom, GameParticipant participant);

    Optional<GameResult> findByParticipant(GameParticipant participant);

    /**
     * 내가 FINISHED한 게임 이력 페이징 조회.
     * GameResult 기준 — GameRoom JOIN FETCH로 방 메타를 N+1 없이 로딩.
     * countQuery 분리: JOIN FETCH가 포함된 쿼리는 COUNT 자동 변환 시 에러 발생하므로 별도 지정.
     */
    @Query(value = "SELECT r FROM GameResult r " +
            "JOIN FETCH r.gameRoom " +
            "JOIN FETCH r.participant " +
            "WHERE r.participant.userId = :userId " +
            "AND r.gameRoom.groupId = :groupId",
            countQuery = "SELECT COUNT(r) FROM GameResult r " +
                    "WHERE r.participant.userId = :userId " +
                    "AND r.gameRoom.groupId = :groupId")
    Page<GameResult> findMyHistory(@Param("userId") UUID userId,
                                   @Param("groupId") Long groupId,
                                   Pageable pageable);

    /**
     * 방별 GameResult 개수 (= 완주자 수) 일괄 집계.
     * 이력 조회 시 participantCount를 N+1 없이 한 번에 가져오기 위한 용도.
     */
    @Query("SELECT r.gameRoom.roomId, COUNT(r) FROM GameResult r " +
            "WHERE r.gameRoom.roomId IN :roomIds GROUP BY r.gameRoom.roomId")
    List<Object[]> countByRoomIds(@Param("roomIds") List<UUID> roomIds);
}
