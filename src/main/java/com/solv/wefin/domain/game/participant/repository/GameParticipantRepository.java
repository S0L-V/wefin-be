package com.solv.wefin.domain.game.participant.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, UUID> {

}
