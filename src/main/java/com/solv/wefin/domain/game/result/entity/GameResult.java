package com.solv.wefin.domain.game.result.entity;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_result",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "participant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_id")
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GameRoom gameRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GameParticipant participant;

    @Column(name = "final_rank", nullable = false)
    private int finalRank;

    @Column(name = "seed_money", nullable = false, precision = 18, scale = 2)
    private BigDecimal seedMoney;

    @Column(name = "final_asset", nullable = false, precision = 18, scale = 2)
    private BigDecimal finalAsset;

    @Column(name = "profit_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal profitRate;

    @Column(name = "total_trades", nullable = false)
    private int totalTrades;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private GameResult(GameRoom gameRoom, GameParticipant participant,
                       int finalRank, BigDecimal seedMoney, BigDecimal finalAsset,
                       BigDecimal profitRate, int totalTrades) {
        this.gameRoom = gameRoom;
        this.participant = participant;
        this.finalRank = finalRank;
        this.seedMoney = seedMoney;
        this.finalAsset = finalAsset;
        this.profitRate = profitRate;
        this.totalTrades = totalTrades;
    }

    @PrePersist
    protected void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }

    public static GameResult create(GameRoom gameRoom, GameParticipant participant,
                                     int finalRank, BigDecimal seedMoney,
                                     BigDecimal finalAsset, BigDecimal profitRate,
                                     int totalTrades) {
        return GameResult.builder()
                .gameRoom(gameRoom)
                .participant(participant)
                .finalRank(finalRank)
                .seedMoney(seedMoney)
                .finalAsset(finalAsset)
                .profitRate(profitRate)
                .totalTrades(totalTrades)
                .build();
    }

    public void updateFinalRank(int rank) {
        this.finalRank = rank;
    }
}
