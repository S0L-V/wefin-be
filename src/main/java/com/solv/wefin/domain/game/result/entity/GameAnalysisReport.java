package com.solv.wefin.domain.game.result.entity;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_analysis_report",
        uniqueConstraints = @UniqueConstraint(columnNames = "participant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameAnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id")
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GameParticipant participant;

    @Column(name = "performance", nullable = false, columnDefinition = "TEXT")
    private String performance;

    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;

    @Column(name = "suggestion", nullable = false, columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private GameAnalysisReport(GameParticipant participant,
                               String performance,
                               String pattern,
                               String suggestion) {
        this.participant = participant;
        this.performance = performance;
        this.pattern = pattern;
        this.suggestion = suggestion;
    }

    @PrePersist
    protected void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }

    public static GameAnalysisReport create(GameParticipant participant,
                                            String performance,
                                            String pattern,
                                            String suggestion) {
        return GameAnalysisReport.builder()
                .participant(participant)
                .performance(performance)
                .pattern(pattern)
                .suggestion(suggestion)
                .build();
    }
}
