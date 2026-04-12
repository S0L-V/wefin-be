package com.solv.wefin.domain.game.participant.entity;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.solv.wefin.domain.game.participant.entity.ParticipantStatus.ACTIVE;
import static com.solv.wefin.domain.game.participant.entity.ParticipantStatus.LEFT;

@Entity
@Table(name = "game_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "room_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "participant_id")
    private UUID participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GameRoom gameRoom;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_leader", nullable = false)
    private Boolean isLeader;

    @Column(name = "seed", precision = 18, scale = 2)
    private BigDecimal seed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ParticipantStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = OffsetDateTime.now();
        if (this.status == null) {
            this.status = ACTIVE;
        }
        if (this.isLeader == null) {
            this.isLeader = false;
        }
    }

    @Builder
    public GameParticipant(GameRoom gameRoom, UUID userId, Boolean isLeader) {
        this.gameRoom = gameRoom;
        this.userId = userId;
        this.isLeader = isLeader != null ? isLeader : false;
        this.status = ACTIVE;
    }

    public static GameParticipant createLeader(GameRoom gameRoom, UUID userId) {
        return GameParticipant.builder()
                .gameRoom(gameRoom)
                .userId(userId)
                .isLeader(true)
                .build();
    }

    public static GameParticipant createMember(GameRoom gameRoom, UUID userId) {
        return GameParticipant.builder()
                .gameRoom(gameRoom)
                .userId(userId)
                .isLeader(false)
                .build();
    }

    public void assignSeed(BigDecimal seed) {
        if (seed == null || seed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("시드머니는 null이거나 음수일 수 없습니다.");
        }
        this.seed = seed;
    }

    /** 매수 시 잔액 차감 (총 매수금 + 수수료) */
    public void deductCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (this.seed.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.seed = this.seed.subtract(amount);
    }

    /** 매도 시 잔액 증가 (총 매도금 - 수수료 - 세금) */
    public void addCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("증가 금액은 0보다 커야 합니다.");
        }
        this.seed = this.seed.add(amount);
    }

    public void leave() {

        this.status = LEFT;
    }

    //방장 퇴장 시
    public void resignLeader() {
        this.isLeader = false;
    }
    //방장 위임
    public void assignLeader() {
        this.isLeader = true;
    }

    public void rejoin() {

        this.status = ACTIVE;
    }
}

