package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.game.order.repository.GameOrderRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.domain.game.room.event.GameRoomEvent;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameEndService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameResultRepository gameResultRepository;
    private final GameOrderRepository gameOrderRepository;
    private final GamePortfolioSnapshotRepository snapshotRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public GameEndInfo endGame(UUID roomId, UUID userId) {

        // 1. 방 조회 + IN_PROGRESS 검증
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (gameRoom.getStatus() != RoomStatus.IN_PROGRESS) {
            if (gameRoom.getStatus() == RoomStatus.FINISHED) {
                throw new BusinessException(ErrorCode.GAME_ALREADY_FINISHED);
            }
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED);
        }

        // 2. 참가자 조회 + ACTIVE 검증
        GameParticipant participant = gameParticipantRepository
                .findByGameRoomAndUserId(gameRoom, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        if (participant.getStatus() == ParticipantStatus.FINISHED) {
            throw new BusinessException(ErrorCode.PARTICIPANT_ALREADY_FINISHED);
        }
        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        // 3. 최신 스냅샷에서 최종 자산/수익률 조회
        GamePortfolioSnapshot latestSnapshot = snapshotRepository
                .findLatestByParticipant(participant)
                .orElse(null);

        BigDecimal finalAsset;
        BigDecimal profitRate;
        if (latestSnapshot != null) {
            finalAsset = latestSnapshot.getTotalAsset();
            profitRate = latestSnapshot.getProfitRate();
        } else {
            // 스냅샷 없음 = 첫 턴에서 바로 종료 → 시드머니 그대로
            finalAsset = gameRoom.getSeed();
            profitRate = BigDecimal.ZERO;
        }

        // 4. 거래 횟수
        int totalTrades = gameOrderRepository.countByParticipant(participant);

        // 5. game_result 저장 (이미 존재하면 스킵 — 턴 자동종료로 먼저 생성된 경우)
        if (!gameResultRepository.existsByGameRoomAndParticipant(gameRoom, participant)) {
            gameResultRepository.save(GameResult.create(
                    gameRoom, participant, 0,
                    gameRoom.getSeed(), finalAsset, profitRate, totalTrades));
        }

        // 6. 방장이면 위임
        boolean wasLeader = participant.getIsLeader();
        if (wasLeader) {
            participant.resignLeader();
        }

        // 7. 참가자 상태 FINISHED 전환
        participant.finish();

        // 8. 남은 ACTIVE 참가자 확인 → 없으면 방 종료 + 순위 확정
        boolean roomFinished = checkAndFinishRoom(gameRoom);

        // 9. 방장 위임 (방이 아직 진행 중이고, 종료한 참가자가 방장이었으면)
        if (!roomFinished && wasLeader) {
            List<GameParticipant> remainingActive =
                    gameParticipantRepository.findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);
            if (!remainingActive.isEmpty()) {
                int randomIndex = ThreadLocalRandom.current().nextInt(remainingActive.size());
                remainingActive.get(randomIndex).assignLeader();
            }
        }

        log.info("[게임 종료] userId={}, roomId={}, finalAsset={}, roomFinished={}",
                userId, roomId, finalAsset, roomFinished);

        // 10. WebSocket 이벤트 발행 (커밋 후 브로드캐스트)
        eventPublisher.publishEvent(
                new GameRoomEvent(roomId, GameRoomEvent.EventType.PARTICIPANT_FINISHED));

        return new GameEndInfo(
                participant.getParticipantId(),
                finalAsset,
                profitRate,
                totalTrades,
                roomFinished);
    }

    /**
     * 모든 ACTIVE 참가자가 FINISHED → 방 FINISHED + 순위 확정.
     * @return 방이 종료되었으면 true
     */
    private boolean checkAndFinishRoom(GameRoom gameRoom) {
        List<GameParticipant> activeParticipants =
                gameParticipantRepository.findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);

        if (!activeParticipants.isEmpty()) {
            return false;
        }

        gameRoom.finish();
        finalizeRanks(gameRoom);
        return true;
    }

    /**
     * 방 전체 종료 시 game_result의 finalRank를 확정한다.
     * finalAsset DESC 기준, 동률이면 같은 순위 (1위, 1위, 3위).
     */
    public void finalizeRanks(GameRoom gameRoom) {
        List<GameResult> results = new ArrayList<>(
                gameResultRepository.findByGameRoomOrderByFinalRankAsc(gameRoom));

        // finalAsset DESC 재정렬
        results.sort(Comparator.comparing(GameResult::getFinalAsset).reversed());

        int prevRank = 1;
        for (int i = 0; i < results.size(); i++) {
            GameResult r = results.get(i);

            int rank;
            if (i == 0) {
                rank = 1;
            } else if (results.get(i - 1).getFinalAsset().compareTo(r.getFinalAsset()) == 0) {
                rank = prevRank;
            } else {
                rank = i + 1;
            }
            prevRank = rank;

            r.updateFinalRank(rank);
        }
    }
}
