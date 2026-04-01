package com.solv.wefin.domain.game.turn.entity;

import com.solv.wefin.domain.game.room.entity.GameRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

import static com.solv.wefin.domain.game.turn.entity.TurnStatus.*;

@Entity
@Table(name = "game_turn",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "turn_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "room_id", nullable = false )
    private UUID turnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @Column(name = "room_id", nullable = false)
    private GameRoom gameRoom;

    //ai 브리핑 테이블 생선 전이라 fk 처리 안 했습니다
    @Column(name = "briefing_id", nullable = false)
    private UUID briefingId;

    @Column(name= "turn_Number", nullable = false)
    private Integer turnNumber;

    @Column(name = "turn_date", nullable = false)
    private LocalDate turnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_status", nullable = false)
    private TurnStatus status;

    @Builder
    private GameTurn(GameRoom gameRoom, Integer turnNumber, LocalDate turnDate ) {
        this.gameRoom = gameRoom;
        this.turnNumber = turnNumber;
        this.turnDate = turnDate;
        this.status = ACTIVE;
        //@GeneratedValue(strategy = GenerationType.UUID)로 turnId 자동 생성
    }

    public static GameTurn createFirst(GameRoom gameRoom){
        return GameTurn.builder()
                .gameRoom(gameRoom)
                .turnNumber(1)
                .turnDate(gameRoom.getStartDate())
                .build();
    }

    public void complete() {
        this.status = COMPLETED;
    }

}
