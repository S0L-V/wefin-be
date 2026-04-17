package com.solv.wefin.domain.game.result.repository;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.result.entity.GameAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameAnalysisReportRepository extends JpaRepository<GameAnalysisReport, UUID> {

    Optional<GameAnalysisReport> findByParticipant(GameParticipant participant);
}
