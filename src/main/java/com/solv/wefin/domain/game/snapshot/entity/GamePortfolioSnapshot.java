package com.solv.wefin.domain.game.snapshot.entity;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "game_portfolio_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"turn_id", "participant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GamePortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false)
    private GameTurn turn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GameParticipant participant;

    @Column(name = "total_asset", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAsset;

    @Column(name = "cash", nullable = false, precision = 18, scale = 2)
    private BigDecimal cash;

    @Column(name = "stock_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal stockValue;

    @Column(name = "profit_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal profitRate;

    @Builder
    private GamePortfolioSnapshot(GameTurn turn, GameParticipant participant,
                                   BigDecimal totalAsset, BigDecimal cash,
                                   BigDecimal stockValue, BigDecimal profitRate) {
        this.turn = turn;
        this.participant = participant;
        this.totalAsset = totalAsset;
        this.cash = cash;
        this.stockValue = stockValue;
        this.profitRate = profitRate;
    }

    public static GamePortfolioSnapshot create(GameTurn turn, GameParticipant participant,
                                                BigDecimal cash, BigDecimal stockValue,
                                                BigDecimal seedMoney) {
        BigDecimal totalAsset = cash.add(stockValue);
        BigDecimal profitRate = totalAsset.subtract(seedMoney)
                .multiply(BigDecimal.valueOf(100))
                .divide(seedMoney, 2, java.math.RoundingMode.HALF_UP);

        return GamePortfolioSnapshot.builder()
                .turn(turn)
                .participant(participant)
                .totalAsset(totalAsset)
                .cash(cash)
                .stockValue(stockValue)
                .profitRate(profitRate)
                .build();
    }
}
