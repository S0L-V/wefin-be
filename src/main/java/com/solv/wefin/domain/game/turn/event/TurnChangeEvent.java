package com.solv.wefin.domain.game.turn.event;

import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 턴 전환 완료 시 발행되는 도메인 이벤트.
 * 리스너에서 WebSocket 브로드캐스트에 사용한다.
 *
 * @param roomId    게임방 ID — WebSocket topic 경로에 사용
 * @param turnNumber 새 턴 번호
 * @param turnDate   새 턴 날짜
 * @param briefingId AI 브리핑 ID (없으면 null)
 * @param snapshots  이번 턴의 모든 참가자 스냅샷 (랭킹 계산용)
 */
public record TurnChangeEvent(
        UUID roomId,
        int turnNumber,
        LocalDate turnDate,
        UUID briefingId,
        List<GamePortfolioSnapshot> snapshots
) {
}
