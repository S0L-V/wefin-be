package com.solv.wefin.domain.game.vote;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 턴 전환 투표 1건의 상태를 관리하는 POJO.
 * DB에 저장하지 않으며, 투표 종료(최대 15초) 시 폐기된다.
 */
public class VoteSession {

    private final UUID roomId;
    private final UUID initiatorId;
    private final int totalCount;
    private final int requiredAgree;
    private final ConcurrentHashMap<UUID, Boolean> votes = new ConcurrentHashMap<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Instant deadline;
    private ScheduledFuture<?> timeoutTask;

    private VoteSession(UUID roomId, UUID initiatorId, int totalCount, Instant deadline) {
        this.roomId = roomId;
        this.initiatorId = initiatorId;
        this.totalCount = totalCount;
        this.requiredAgree = totalCount / 2 + 1;
        this.deadline = deadline;
    }

    public static VoteSession create(UUID roomId, UUID initiatorId, int totalCount,
                                     int timeoutSeconds) {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        return new VoteSession(roomId, initiatorId, totalCount, deadline);
    }

    /**
     * 투표를 기록한다.
     *
     * @return true면 정상 기록, false면 이미 투표한 사용자 (중복)
     */
    public boolean castVote(UUID userId, boolean agree) {
        return votes.putIfAbsent(userId, agree) == null;
    }

    /**
     * 현재 찬성 수.
     */
    public long getAgreeCount() {
        return votes.values().stream().filter(v -> v).count();
    }

    /**
     * 현재 반대 수.
     */
    public long getDisagreeCount() {
        return votes.values().stream().filter(v -> !v).count();
    }

    /**
     * 전원 투표 완료 여부.
     */
    public boolean isAllVoted() {
        return votes.size() >= totalCount;
    }

    /**
     * 과반수 찬성 도달 여부.
     */
    public boolean isMajorityReached() {
        return getAgreeCount() >= requiredAgree;
    }

    /**
     * 부결 확정 여부: 반대가 (총원 - 과반수 + 1) 이상이면 찬성이 과반수에 도달 불가.
     */
    public boolean isRejectionCertain() {
        return getDisagreeCount() > totalCount - requiredAgree;
    }

    /**
     * 종료 처리를 시도한다. CAS로 딱 1번만 true를 반환.
     * 타이머 만료와 마지막 투표가 동시에 호출해도 1회만 실행됨을 보장.
     */
    public boolean tryComplete() {
        return completed.compareAndSet(false, true);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    /**
     * 타이머를 취소한다. 과반수 조기 달성 시 호출.
     */
    public void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getInitiatorId() {
        return initiatorId;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getRequiredAgree() {
        return requiredAgree;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public int getVotedCount() {
        return votes.size();
    }
}
