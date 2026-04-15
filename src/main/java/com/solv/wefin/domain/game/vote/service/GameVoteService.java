package com.solv.wefin.domain.game.vote.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.turn.service.TurnAdvanceService;
import com.solv.wefin.domain.game.vote.VoteBroadcaster;
import com.solv.wefin.domain.game.vote.VoteSession;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameVoteService {

    private static final int VOTE_TIMEOUT_SECONDS = 15;

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final TurnAdvanceService turnAdvanceService;
    private final VoteBroadcaster voteBroadcaster;
    private final ScheduledExecutorService voteScheduler;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<UUID, VoteSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 투표를 시작하거나, 진행 중인 투표에 참여한다.
     *
     * - 진행 중인 투표 없음 + 방장 -> 새 세션 생성 + 방장 찬성 1표 + 15초 타이머
     * - 진행 중인 투표 있음 -> 투표 기록
     *
     * @return 투표 기록 후의 VoteSession
     */
    public VoteSession vote(UUID roomId, UUID userId, boolean agree) {
        // 1. 게임방 상태 검증
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (gameRoom.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED);
        }

        // 2. 참가자 검증
        GameParticipant participant = gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 3. 세션 조회 (만료 세션 자동 정리)
        VoteSession session = activeSessions.get(roomId);

        if (session != null && Instant.now().isAfter(session.getDeadline())) {
            activeSessions.remove(roomId, session);
            log.warn("[투표] 만료 세션 정리: roomId={}", roomId);
            session = null;
        }

        if (session == null) {
            // 진행 중인 투표 없음 -> 새 투표 시작 (방장만 가능)
            return startNewVote(roomId, gameRoom, participant, userId, agree);
        }

        // 4. 진행 중인 투표에 참여
        if (session.isCompleted()) {
            throw new BusinessException(ErrorCode.VOTE_NOT_IN_PROGRESS);
        }

        if (!session.castVote(userId, agree)) {
            throw new BusinessException(ErrorCode.VOTE_ALREADY_CAST);
        }

        log.info("[투표] 기록: roomId={}, userId={}, agree={}, 현황={}/{}",
                roomId, userId, agree, session.getAgreeCount(), session.getTotalCount());

        // 5. 브로드캐스트: 투표 현황
        voteBroadcaster.broadcastUpdate(
                roomId, session.getAgreeCount(), session.getDisagreeCount(), session.getTotalCount());

        // 6. 판정 시도
        checkAndResolve(roomId, session);

        return session;
    }

    /**
     * 새 투표 세션을 시작한다. 방장만 가능.
     */
    private VoteSession startNewVote(UUID roomId, GameRoom gameRoom, GameParticipant participant,
                                     UUID userId, boolean agree) {
        // 방장 확인 -- GameRoom.userId가 아닌 participant.isLeader로 판단해야
        // 방장 위임 후에도 새 방장이 투표를 시작할 수 있다.
        if (!participant.getIsLeader()) {
            throw new BusinessException(ErrorCode.ROOM_NOT_HOST);
        }

        // 활성 참가자 수
        int totalCount = gameParticipantRepository
                .countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);

        // computeIfAbsent로 동시 생성 방지
        VoteSession session = activeSessions.computeIfAbsent(roomId,
                key -> VoteSession.create(roomId, userId, totalCount, VOTE_TIMEOUT_SECONDS));

        // 다른 스레드가 먼저 생성한 경우 (방장이 아닌 세션)
        if (!session.getInitiatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.VOTE_ALREADY_IN_PROGRESS);
        }

        // 방장은 투표 시작 시 무조건 찬성 (feature-spec: "방장 찬성 1표 자동")
        session.castVote(userId, true);

        log.info("[투표] 시작: roomId={}, initiator={}, totalCount={}", roomId, userId, totalCount);

        // 타이머 예약
        session.setTimeoutTask(voteScheduler.schedule(
                () -> expireVote(roomId),
                VOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // 닉네임 조회 후 브로드캐스트
        String nickname = userRepository.findById(userId)
                .map(user -> user.getNickname())
                .orElse("알 수 없음");

        voteBroadcaster.broadcastStart(roomId, nickname, totalCount, VOTE_TIMEOUT_SECONDS);

        // 방장 투표 현황도 브로드캐스트
        voteBroadcaster.broadcastUpdate(
                roomId, session.getAgreeCount(), session.getDisagreeCount(), session.getTotalCount());

        // 1인 방 등 즉시 과반수 도달 시
        checkAndResolve(roomId, session);

        return session;
    }

    /**
     * 과반수 도달 / 부결 확정 / 전원 투표 완료 시 즉시 결과를 확정한다.
     */
    private void checkAndResolve(UUID roomId, VoteSession session) {
        boolean shouldResolve = session.isMajorityReached()
                || session.isRejectionCertain()
                || session.isAllVoted();

        if (!shouldResolve) {
            return;
        }

        // CAS: 타이머와 경합 시 1회만 실행
        if (!session.tryComplete()) {
            return;
        }

        session.cancelTimeout();
        resolve(roomId, session);
    }

    /**
     * 타이머 만료 시 호출. 미투표자는 기권 처리.
     */
    private void expireVote(UUID roomId) {
        VoteSession session = activeSessions.get(roomId);
        if (session == null) {
            return;
        }

        // CAS: 마지막 투표와 경합 시 1회만 실행
        if (!session.tryComplete()) {
            return;
        }

        log.info("[투표] 타이머 만료: roomId={}, 찬성={}, 반대={}, 미투표={}",
                roomId, session.getAgreeCount(), session.getDisagreeCount(),
                session.getTotalCount() - session.getVotedCount());

        resolve(roomId, session);
    }

    /**
     * 투표 결과를 확정하고 후속 처리를 수행한다.
     */
    private void resolve(UUID roomId, VoteSession session) {
        boolean passed = session.isMajorityReached();

        log.info("[투표] 결과: roomId={}, passed={}, 찬성={}, 반대={}",
                roomId, passed, session.getAgreeCount(), session.getDisagreeCount());

        // 통과 시 턴 전환을 먼저 시도 — 실패하면 부결로 전환
        if (passed) {
            try {
                turnAdvanceService.advanceTurn(roomId, session.getInitiatorId());
            } catch (Exception e) {
                log.error("[투표] 턴 전환 실패: roomId={}, error={}", roomId, e.getMessage(), e);
                passed = false;
            }
        }

        // 브로드캐스트: 투표 결과 (턴 전환 성공 여부 반영)
        voteBroadcaster.broadcastResult(
                roomId, passed, session.getAgreeCount(), session.getDisagreeCount());

        // 세션 제거
        activeSessions.remove(roomId);
    }
}
