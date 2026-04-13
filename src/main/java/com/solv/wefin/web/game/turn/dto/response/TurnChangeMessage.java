package com.solv.wefin.web.game.turn.dto.response;

import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket /topic/rooms/{roomId}/turn 으로 브로드캐스트되는 턴 전환 메시지.
 */
public record TurnChangeMessage(
        String type,
        int turnNumber,
        LocalDate turnDate,
        UUID briefingId,
        List<RankingEntry> rankings
) {

    public record RankingEntry(
            int rank,
            UUID userId,
            String userName,
            BigDecimal totalAsset,
            BigDecimal profitRate
    ) {
    }

    /**
     * @param nicknameMap userId → nickname 매핑 (User 테이블에서 조회)
     */
    public static TurnChangeMessage from(int turnNumber, LocalDate turnDate,
                                          UUID briefingId,
                                          List<GamePortfolioSnapshot> snapshots,
                                          Map<UUID, String> nicknameMap) {
        AtomicInteger rankCounter = new AtomicInteger(1);

        List<RankingEntry> rankings = snapshots.stream()
                .sorted(Comparator.comparing(GamePortfolioSnapshot::getTotalAsset).reversed())
                .map(s -> {
                    UUID userId = s.getParticipant().getUserId();
                    return new RankingEntry(
                            rankCounter.getAndIncrement(),
                            userId,
                            nicknameMap.getOrDefault(userId, "알 수 없음"),
                            s.getTotalAsset(),
                            s.getProfitRate());
                })
                .toList();

        return new TurnChangeMessage("TURN_CHANGE", turnNumber, turnDate, briefingId, rankings);
    }
}
