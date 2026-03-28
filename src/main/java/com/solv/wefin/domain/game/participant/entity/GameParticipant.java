package com.solv.wefin.domain.game.participant.entity;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name="status", nullable = false)
    private String status;

    @Column(name="joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "ACTIVE";
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
        this.status = "ACTIVE";
    }

    public void assignSeed(Long seed) {
        this.seed = seed;
    }

    public void leave() {
        this.status = "LEFT";
    }

}
