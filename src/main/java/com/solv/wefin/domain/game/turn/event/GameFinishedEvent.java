package com.solv.wefin.domain.game.turn.event;

import java.util.UUID;

/**
 * 게임 종료 시 발행되는 도메인 이벤트.
 * 리스너에서 WebSocket 브로드캐스트 → 프론트가 결과 페이지로 리다이렉트한다.
 *
 * @param roomId 게임방 ID
 */
public record GameFinishedEvent(UUID roomId) {
}
