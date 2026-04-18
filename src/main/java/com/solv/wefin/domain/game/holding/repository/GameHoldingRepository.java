package com.solv.wefin.domain.game.holding.repository;

import com.solv.wefin.domain.game.holding.entity.GameHolding;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameHoldingRepository extends JpaRepository<GameHolding, UUID> {

    /** 매수 시: 기존 보유 종목이 있는지 조회 (있으면 addQuantity, 없으면 create) */
    Optional<GameHolding> findByParticipantAndStockInfo(GameParticipant participant, StockInfo stockInfo);

    /** 포트폴리오/보유종목 조회: 수량이 0보다 큰 보유종목만 조회 */
    List<GameHolding> findAllByParticipantAndQuantityGreaterThan(GameParticipant participant, int quantity);
}
