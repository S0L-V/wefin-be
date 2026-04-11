package com.solv.wefin.domain.game.holding.entity;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "game_holding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"participant_id", "symbol"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "holding_id")
    private UUID holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GameParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false)
    private StockInfo stockInfo;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "avg_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal avgPrice;

    @Builder
    private GameHolding(GameParticipant participant, StockInfo stockInfo,
                        String stockName, Integer quantity, BigDecimal avgPrice) {
        this.participant = participant;
        this.stockInfo = stockInfo;
        this.stockName = stockName;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    public static GameHolding create(GameParticipant participant, StockInfo stockInfo,
                                     Integer quantity, BigDecimal avgPrice) {
        return GameHolding.builder()
                .participant(participant)
                .stockInfo(stockInfo)
                .stockName(stockInfo.getStockName())
                .quantity(quantity)
                .avgPrice(avgPrice)
                .build();
    }

    /**
     * 추가 매수 시 수량 증가 + 평균 매수가 가중평균 재계산
     * 새 평균가 = (기존수량 × 기존평균가 + 추가수량 × 매수가) / 전체수량
     */
    public void addQuantity(Integer buyQuantity, BigDecimal buyPrice) {
        BigDecimal totalCost = this.avgPrice.multiply(BigDecimal.valueOf(this.quantity))
                .add(buyPrice.multiply(BigDecimal.valueOf(buyQuantity)));
        this.quantity += buyQuantity;
        this.avgPrice = totalCost.divide(BigDecimal.valueOf(this.quantity), 2, RoundingMode.HALF_UP);
    }

    /**
     * 매도 시 수량 감소. 평균 매수가는 변하지 않음.
     * 전량 매도 시에는 이 메서드를 호출하지 말고 Service에서 바로 delete 해야 한다 (CHECK > 0 제약조건).
     */
    public void reduceQuantity(Integer sellQuantity) {
        if (sellQuantity <= 0) {
            throw new IllegalArgumentException("매도 수량은 0보다 커야 합니다.");
        }
        if (sellQuantity >= this.quantity) {
            throw new IllegalStateException("전량 매도는 reduceQuantity가 아닌 delete로 처리해야 합니다.");
        }
        this.quantity -= sellQuantity;
    }
}
