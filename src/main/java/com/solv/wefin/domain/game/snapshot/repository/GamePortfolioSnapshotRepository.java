package com.solv.wefin.domain.game.snapshot.repository;

import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GamePortfolioSnapshotRepository extends JpaRepository<GamePortfolioSnapshot, UUID> {
}
