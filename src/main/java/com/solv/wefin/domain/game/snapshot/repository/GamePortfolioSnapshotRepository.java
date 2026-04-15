package com.solv.wefin.domain.game.snapshot.repository;

import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GamePortfolioSnapshotRepository extends JpaRepository<GamePortfolioSnapshot, UUID> {

    List<GamePortfolioSnapshot> findByTurnOrderByTotalAssetDesc(GameTurn turn);
}
