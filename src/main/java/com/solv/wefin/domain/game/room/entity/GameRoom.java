package com.solv.wefin.domain.game.room.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name= "game_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name= "room_id")
    private UUID roomId;

    @Column(name= "group_id", nullable = false)
    private Long  groupId;

    @Column(name = "user_id", nullable = false)
    private UUID  userId;

    @Column(name = "seed", nullable = false)
    private Long  seed;

    @Column(name ="period_month", nullable = false)
    private Integer  periodMonth;

    @Column(name ="move_days", nullable = false)
    private Integer  moveDays;

    @Column(name ="start_date", nullable = false)
    private LocalDate startDate;

    @Column(name ="end_date", nullable = false)
    private LocalDate endDate;

    @Column(name ="status", nullable = false)
    private String status;

    @Column(name ="created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name ="started_at")
    private LocalDateTime startedAt;

    @PrePersist
    protected  void oncCreated() {
        this.createdAt = LocalDateTime.now();
        if(this.status==null) {
            this.status="WAITING";
        }
    }

    @Builder
    public GameRoom (Long groupId, UUID userId, Long seed, Integer periodMonth, Integer moveDays, LocalDate startDate, LocalDate endDate) {
      this.groupId = groupId;
      this.userId = userId;
      this.seed = seed;
      this.periodMonth = periodMonth;
      this.moveDays = moveDays;
      this.startDate = startDate;
      this.endDate = endDate;
      this.status = "WAITING";
    }

    public void start() {
        this.status = "IN_PROGRESS";
        this.startedAt = LocalDateTime.now();
    }

    public void finish() {
        this.status = "FINISHED";
    }
}
