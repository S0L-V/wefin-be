package com.solv.wefin.domain.game.order.repository;

import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameOrderRepository extends JpaRepository<GameOrder, UUID> {

    int countByParticipant(GameParticipant participant);

    /**
     * 참가자의 매매 내역 전체 조회 (결과 페이지용).
     * - JOIN FETCH t: 응답의 turnNumber/turnDate 접근 시 N+1 방지.
     * - ORDER BY: 1차 turnNumber ASC(시간순), 2차 orderId ASC(같은 GET 요청 반복 시 순서 결정성 보장).
     *   같은 턴 내 거래 순서는 비즈니스 의미 없음 — 단순 결정성 확보용.
     */
    @Query("SELECT o FROM GameOrder o "
         + "JOIN FETCH o.turn t "
         + "WHERE o.participant = :participant "
         + "ORDER BY t.turnNumber ASC, o.orderId ASC")
    List<GameOrder> findByParticipantOrderByTurnNumber(@Param("participant") GameParticipant participant);
}
