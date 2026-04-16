package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
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
    private final UserRepository userRepository;

    /**
     * 게임 결과 조회.
     * 본인이 FINISHED 상태여야 조회 가능.
     * 순위는 매 요청마다 finalAsset DESC로 동적 계산(standard competition rank: 1, 1, 3) —
     * 방이 아직 IN_PROGRESS면 finalRank=0인 레코드가 섞일 수 있으므로 필드 값을 신뢰하지 않는다.
     */
    public GameResultInfo getGameResult(UUID roomId, UUID userId) {

        // 1. 방 조회
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참가자 검증 (FINISHED 전용)
        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.FINISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        // 3. 결과 조회 (finalAsset 내림차순)
        //    2차 정렬(createdAt ASC): 동률 시 DB 반환 순서 비결정성을 제거 —
        //    같은 요청을 반복해도 동률 참가자들의 순서가 바뀌지 않도록 정렬 결정성을 보장한다.
        //    (강제 종료 경로에서는 같은 트랜잭션 내 일괄 저장이라 createdAt이 거의 동일하므로
        //     "먼저 종료한 순" 의미보다는 결정성 보장 용도가 우선이다.)
        List<GameResult> results = gameResultRepository
                .findByGameRoomOrderByFinalAssetDescCreatedAtAsc(gameRoom);

        // 4. 닉네임 일괄 조회 (N+1 방지)
        List<UUID> userIds = results.stream()
                .map(r -> r.getParticipant().getUserId())
                .toList();
        Map<UUID, String> nicknameMap = buildNicknameMap(userIds);

        // 5. standard competition rank 부여 (동률이면 같은 순위, 다음은 건너뜀: 1, 1, 3)
        List<GameResultInfo.RankingEntry> rankings = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            GameResult r = results.get(i);
            int rank = (i == 0) ? 1
                    : results.get(i - 1).getFinalAsset().compareTo(r.getFinalAsset()) == 0
                            ? rankings.get(i - 1).rank()
                            : i + 1;

            rankings.add(new GameResultInfo.RankingEntry(
                    rank,
                    nicknameMap.getOrDefault(r.getParticipant().getUserId(), "알 수 없음"),
                    r.getSeedMoney(),
                    r.getFinalAsset(),
                    r.getProfitRate(),
                    r.getTotalTrades()));
        }

        return new GameResultInfo(
                gameRoom.getRoomId(),
                gameRoom.getStartDate(),
                gameRoom.getEndDate(),
                rankings);
    }

    private Map<UUID, String> buildNicknameMap(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getUserId(),
                        user -> user.getNickname()));
    }
}
