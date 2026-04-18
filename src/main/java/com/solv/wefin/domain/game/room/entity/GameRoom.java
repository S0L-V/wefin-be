package com.solv.wefin.domain.game.room.entity;

import static com.solv.wefin.domain.game.room.entity.RoomStatus.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(name = "seed", nullable = false, precision = 18, scale = 2)
    private BigDecimal seed;

    @Column(name ="period_month", nullable = false)
    private Integer  periodMonth;

    @Column(name ="move_days", nullable = false)
    private Integer  moveDays;

    @Column(name ="start_date", nullable = false)
    private LocalDate startDate;

    @Column(name ="end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name ="status", nullable = false)
    private RoomStatus status;

    @Column(name ="created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name ="started_at")
    private OffsetDateTime startedAt;

    @PrePersist
    protected  void onCreated() {
        this.createdAt = OffsetDateTime.now();
        if(this.status==null) {
            this.status=WAITING;
        }
    }

    @Builder
    public GameRoom (Long groupId, UUID userId, BigDecimal seed, Integer periodMonth, Integer moveDays, LocalDate startDate, LocalDate endDate) {
      this.groupId = groupId;
      this.userId = userId;
      this.seed = seed;
      this.periodMonth = periodMonth;
      this.moveDays = moveDays;
      this.startDate = startDate;
      this.endDate = endDate;
      this.status = WAITING;
    }

    public static GameRoom create(Long groupId, UUID userId, BigDecimal seed, Integer periodMonth, Integer moveDays, LocalDate startDate, LocalDate endDate) {
        return GameRoom.builder()
                .groupId(groupId)
                .userId(userId)
                .seed(seed)
                .periodMonth(periodMonth)
                .moveDays(moveDays)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }


    public void start() {
        this.status = IN_PROGRESS;
        this.startedAt = OffsetDateTime.now();
    }

    public void finish() {
        this.status = FINISHED;
    }
}
