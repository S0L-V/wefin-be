package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.order.dto.OrderHistoryInfo;
import com.solv.wefin.domain.game.order.repository.GameOrderRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.dto.SnapshotInfo;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameResultService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameResultRepository gameResultRepository;
    private final GamePortfolioSnapshotRepository snapshotRepository;
    private final GameOrderRepository gameOrderRepository;
    private final UserRepository userRepository;

    /**
     * 게임 결과 조회.
     * 본인이 FINISHED 상태이면 호출 가능 (방이 아직 IN_PROGRESS여도 OK).
     *
     * rankings 노출 정책:
     * - 방 FINISHED: 전체 참가자의 finalAsset 기준 순위 노출 (본인 행은 isMine=true)
     * - 방 IN_PROGRESS: rankings 빈 배열. 일찍 endGame()한 사용자도 결과 페이지 진입은 가능하지만,
     *   다른 참가자들의 게임 진행이 아직 안 끝나서 부분 랭킹은 의미가 없으므로 노출하지 않는다.
     *
     * 순위는 매 요청마다 finalAsset DESC로 동적 계산(standard competition rank: 1, 1, 3) —
     * GameResult.finalRank 필드는 방 종료 시 finalizeRanks()로 확정되지만,
     * 응답 일관성을 위해 동적 계산을 유지한다.
     */
    public GameResultInfo getGameResult(UUID roomId, UUID userId) {

        // 1. 방 조회
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참가자 검증 (FINISHED 전용)
        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.FINISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        // 3. 방 종료 여부 — rankings 노출 분기 기준
        boolean roomFinished = gameRoom.getStatus() == RoomStatus.FINISHED;

        // 4. 방이 아직 진행 중이면 rankings는 빈 배열로 응답
        if (!roomFinished) {
            return new GameResultInfo(
                    gameRoom.getRoomId(),
                    gameRoom.getStartDate(),
                    gameRoom.getEndDate(),
                    false,
                    List.of());
        }

        // 5. 결과 조회 (finalAsset 내림차순)
        //    2차 정렬(createdAt ASC): 동률 시 DB 반환 순서 비결정성을 제거 —
        //    같은 요청을 반복해도 동률 참가자들의 순서가 바뀌지 않도록 정렬 결정성을 보장한다.
        List<GameResult> results = gameResultRepository
                .findByGameRoomOrderByFinalAssetDescCreatedAtAsc(gameRoom);

        // 6. 닉네임 일괄 조회 (N+1 방지)
        List<UUID> userIds = results.stream()
                .map(r -> r.getParticipant().getUserId())
                .toList();
        Map<UUID, String> nicknameMap = buildNicknameMap(userIds);

        // 7. standard competition rank 부여 + 본인 행 isMine=true 마킹
        //    JWT 기반 userId로 매칭 — 닉네임 매칭 대비 동명이인/닉네임 변경에 안전.
        List<GameResultInfo.RankingEntry> rankings = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            GameResult r = results.get(i);
            int rank = (i == 0) ? 1
                    : results.get(i - 1).getFinalAsset().compareTo(r.getFinalAsset()) == 0
                            ? rankings.get(i - 1).rank()
                            : i + 1;

            boolean isMine = r.getParticipant().getUserId().equals(userId);

            rankings.add(new GameResultInfo.RankingEntry(
                    rank,
                    nicknameMap.getOrDefault(r.getParticipant().getUserId(), "알 수 없음"),
                    r.getFinalAsset(),
                    isMine));
        }

        return new GameResultInfo(
                gameRoom.getRoomId(),
                gameRoom.getStartDate(),
                gameRoom.getEndDate(),
                true,
                rankings);
    }

    /**
     * 자산 변동 그래프용 턴별 스냅샷 조회 (본인 전용).
     * 본인이 FINISHED 상태여야 조회 가능. 본인만 FINISHED면 방 IN_PROGRESS여도 조회 OK.
     * 결과 페이지는 본인 데이터만 노출하는 정책 — 다른 참가자 스냅샷은 조회 불가.
     */
    public List<SnapshotInfo> getSnapshots(UUID roomId, UUID userId) {

        // 1. 방 조회
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 요청자 검증 (FINISHED 전용)
        GameParticipant requester = gameParticipantRepository
                .findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.FINISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        // 3. 스냅샷 조회 (turnNumber ASC, JOIN FETCH로 N+1 방지)
        return snapshotRepository.findByParticipantOrderByTurnNumber(requester).stream()
                .map(s -> new SnapshotInfo(
                        s.getTurn().getTurnNumber(),
                        s.getTurn().getTurnDate(),
                        s.getTotalAsset(),
                        s.getCash(),
                        s.getStockValue(),
                        s.getProfitRate()))
                .toList();
    }

    /**
     * 본인의 매매 내역 전체 조회 (결과 페이지 매매 내역 테이블용).
     * 본인이 FINISHED 상태여야 조회 가능. 본인만 FINISHED면 방 IN_PROGRESS여도 조회 OK.
     * 다른 참가자의 매매 내역은 비공개 — participantId 파라미터 없음.
     * 정렬: turnNumber ASC, orderId ASC (같은 턴 내 거래 순서는 비즈니스 의미 없으나
     * 같은 GET 요청 반복 시 행 순서 결정성 확보 — UX 깜빡임 방지).
     */
    public List<OrderHistoryInfo> getOrderHistory(UUID roomId, UUID userId) {

        // 1. 방 조회
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 요청자 검증 (FINISHED 전용)
        GameParticipant requester = gameParticipantRepository
                .findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.FINISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        // 3. 매매 내역 조회 (JOIN FETCH로 N+1 방지, 정렬 결정성 보장)
        return gameOrderRepository.findByParticipantOrderByTurnNumber(requester).stream()
                .map(o -> new OrderHistoryInfo(
                        o.getOrderId(),
                        o.getTurn().getTurnNumber(),
                        o.getTurn().getTurnDate(),
                        o.getStockInfo().getSymbol(),
                        o.getStockName(),
                        o.getOrderType(),
                        o.getQuantity(),
                        o.getOrderPrice(),
                        o.getFee(),
                        o.getTax()))
                .toList();
    }

    private Map<UUID, String> buildNicknameMap(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getUserId(),
                        user -> user.getNickname()));
    }
}
