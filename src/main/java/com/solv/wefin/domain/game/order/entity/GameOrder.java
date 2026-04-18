package com.solv.wefin.domain.game.order.entity;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "game_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id")
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private GameParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false)
    private GameTurn turn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false)
    private StockInfo stockInfo;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "order_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal orderPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal fee;

    @Column(name = "tax", nullable = false, precision = 18, scale = 2)
    private BigDecimal tax;

    @Builder
    private GameOrder(GameParticipant participant, GameTurn turn, StockInfo stockInfo,
                      String stockName, OrderType orderType, BigDecimal orderPrice,
                      Integer quantity, BigDecimal fee, BigDecimal tax) {
        this.participant = participant;
        this.turn = turn;
        this.stockInfo = stockInfo;
        this.stockName = stockName;
        this.orderType = orderType;
        this.orderPrice = orderPrice;
        this.quantity = quantity;
        this.fee = fee;
        this.tax = tax;
    }

    public static GameOrder createBuy(GameParticipant participant, GameTurn turn,
                                      StockInfo stockInfo, BigDecimal orderPrice,
                                      Integer quantity, BigDecimal fee) {
        return GameOrder.builder()
                .participant(participant)
                .turn(turn)
                .stockInfo(stockInfo)
                .stockName(stockInfo.getStockName())
                .orderType(OrderType.BUY)
                .orderPrice(orderPrice)
                .quantity(quantity)
                .fee(fee)
                .tax(BigDecimal.ZERO)
                .build();
    }

    public static GameOrder createSell(GameParticipant participant, GameTurn turn,
                                       StockInfo stockInfo, BigDecimal orderPrice,
                                       Integer quantity, BigDecimal fee, BigDecimal tax) {
        return GameOrder.builder()
                .participant(participant)
                .turn(turn)
                .stockInfo(stockInfo)
                .stockName(stockInfo.getStockName())
                .orderType(OrderType.SELL)
                .orderPrice(orderPrice)
                .quantity(quantity)
                .fee(fee)
                .tax(tax)
                .build();
    }
}
