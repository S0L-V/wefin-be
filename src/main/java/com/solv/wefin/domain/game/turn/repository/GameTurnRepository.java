package com.solv.wefin.domain.game.turn.repository;

import com.solv.wefin.domain.game.turn.entity.GameTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameTurnRepository extends JpaRepository<GameTurn, UUID> {


}
