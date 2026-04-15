package com.solv.wefin.domain.game.snapshot.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.dto.RankingInfo;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRankingService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GamePortfolioSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;

    public List<RankingInfo> getRankings(UUID roomId, UUID userId) {

        // 1. 방 조회 + 상태 검증
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (gameRoom.getStatus() == RoomStatus.WAITING) {
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED);
        }

        // 2. 참가자 검증
        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 3. 최신 완료 턴의 스냅샷 조회
        //    스냅샷은 턴 전환 시 "완료되는 턴"에 저장됨
        //    → 완료 턴 중 turnNumber가 가장 큰 것 = 최신 스냅샷
        List<GamePortfolioSnapshot> snapshots = gameTurnRepository
                .findFirstByGameRoomAndStatusOrderByTurnNumberDesc(gameRoom, TurnStatus.COMPLETED)
                .map(snapshotRepository::findByTurnOrderByTotalAssetDesc)
                .orElse(List.of());

        if (snapshots.isEmpty()) {
            // 첫 턴: 스냅샷 없음 → 전원 seedMoney 동률
            return buildInitialRankings(gameRoom);
        }

        // 4. 닉네임 일괄 조회
        List<UUID> userIds = snapshots.stream()
                .map(s -> s.getParticipant().getUserId())
                .toList();

        Map<UUID, String> nicknameMap = buildNicknameMap(userIds);

        // 5. 순위 부여 — 동률(같은 totalAsset)이면 같은 순위 (1위, 1위, 3위)
        List<RankingInfo> rankings = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            GamePortfolioSnapshot s = snapshots.get(i);
            int rank = (i == 0) ? 1
                    : snapshots.get(i - 1).getTotalAsset().compareTo(s.getTotalAsset()) == 0
                            ? rankings.get(i - 1).rank()
                            : i + 1;

            rankings.add(new RankingInfo(
                    rank,
                    s.getParticipant().getUserId(),
                    nicknameMap.getOrDefault(s.getParticipant().getUserId(), "알 수 없음"),
                    s.getTotalAsset(),
                    s.getProfitRate()));
        }

        return rankings;
    }

    /**
     * 첫 턴: 스냅샷이 없으므로 전원 seedMoney 기준 동률 랭킹 생성.
     */
    private List<RankingInfo> buildInitialRankings(GameRoom gameRoom) {
        List<GameParticipant> participants = gameParticipantRepository
                .findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);

        List<UUID> userIds = participants.stream()
                .map(GameParticipant::getUserId)
                .toList();

        Map<UUID, String> nicknameMap = buildNicknameMap(userIds);

        BigDecimal seedMoney = gameRoom.getSeed();
        AtomicInteger rankCounter = new AtomicInteger(1);

        return participants.stream()
                .map(p -> new RankingInfo(
                        rankCounter.getAndIncrement(),
                        p.getUserId(),
                        nicknameMap.getOrDefault(p.getUserId(), "알 수 없음"),
                        seedMoney,
                        BigDecimal.ZERO))
                .toList();
    }

    private Map<UUID, String> buildNicknameMap(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getUserId(),
                        user -> user.getNickname()
                ));
    }
}
