package com.solv.wefin.domain.game.participant.entity;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    @Column(name="user_id", nullable = false)
    private UUID userId;

    @Column(name="is_leader", nullable = false)
    private Boolean isLeader;

    @Column(name="seed")
    private Long seed;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private ParticipantStatus status;

    @Column(name="joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = OffsetDateTime.now();
        if (this.status == null) {
            this.status = ACTIVE;
        }
        if(this.isLeader == null) {
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

    public void assignSeed(Long seed) {
        this.seed = seed;
    }

    public void leave() {
        this.status = LEFT;
    }

}
