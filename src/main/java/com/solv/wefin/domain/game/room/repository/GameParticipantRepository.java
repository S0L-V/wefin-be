package com.solv.wefin.domain.game.room.repository;

import com.solv.wefin.domain.game.room.entity.GameParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, UUID> {

}
