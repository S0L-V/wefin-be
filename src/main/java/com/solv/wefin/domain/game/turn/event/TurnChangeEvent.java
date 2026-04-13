package com.solv.wefin.domain.game.turn.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 턴 전환 완료 시 발행되는 도메인 이벤트.
 * 리스너에서 WebSocket 브로드캐스트에 사용한다.
 *
 * <p>Entity 대신 {@link SnapshotData}를 사용하여,
 * AFTER_COMMIT 단계에서 Lazy Loading 없이 안전하게 접근할 수 있다.
 *
 * @param roomId     게임방 ID — WebSocket topic 경로에 사용
 * @param turnNumber 새 턴 번호
 * @param turnDate   새 턴 날짜
 * @param briefingId AI 브리핑 ID (없으면 null)
 * @param snapshots  이번 턴의 모든 참가자 스냅샷 데이터 (랭킹 계산용)
 */
public record TurnChangeEvent(
        UUID roomId,
        int turnNumber,
        LocalDate turnDate,
        UUID briefingId,
        List<SnapshotData> snapshots
) {

    /**
     * AFTER_COMMIT 시점에서 안전하게 사용할 수 있는 스냅샷 경량 DTO.
     * 트랜잭션 안에서 Entity → SnapshotData로 변환해서 이벤트에 담는다.
     */
    public record SnapshotData(
            UUID userId,
            BigDecimal totalAsset,
            BigDecimal profitRate
    ) {
    }
}
