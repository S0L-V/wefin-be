package com.solv.wefin.domain.game.snapshot.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePortfolioSnapshotRepository extends JpaRepository<GamePortfolioSnapshot, UUID> {

    List<GamePortfolioSnapshot> findByTurnOrderByTotalAssetDesc(GameTurn turn);

    @Query("SELECT s FROM GamePortfolioSnapshot s " +
            "JOIN FETCH s.turn t " +
            "WHERE s.participant = :participant " +
            "ORDER BY t.turnNumber ASC")
    List<GamePortfolioSnapshot> findByParticipantOrderByTurnNumber(
            @Param("participant") GameParticipant participant);

    @Query("SELECT s FROM GamePortfolioSnapshot s " +
            "JOIN s.turn t " +
            "WHERE s.participant = :participant " +
            "ORDER BY t.turnNumber DESC " +
            "LIMIT 1")
    Optional<GamePortfolioSnapshot> findLatestByParticipant(
            @Param("participant") GameParticipant participant);
}
