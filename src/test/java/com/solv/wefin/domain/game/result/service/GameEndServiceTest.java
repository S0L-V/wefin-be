package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.game.order.repository.GameOrderRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameEndServiceTest {

    @InjectMocks
    private GameEndService gameEndService;

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private GameResultRepository gameResultRepository;
    @Mock
    private GameOrderRepository gameOrderRepository;
    @Mock
    private GamePortfolioSnapshotRepository snapshotRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-a000-000000000003");
    private static final Long GROUP_ID = 1L;
    private static final LocalDate START_DATE = LocalDate.of(2022, 3, 2);
    private static final BigDecimal SEED = new BigDecimal("10000000");

    @Nested
    @DisplayName("개별 게임 종료 성공")
    class SuccessTests {

        @Test
        @DisplayName("스냅샷 있는 참가자 종료 — 최종 자산/수익률 반환, 다른 참가자 남아있으면 방은 유지")
        void endGame_withSnapshot_roomNotFinished() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);

            GameTurn turn = GameTurn.createFirst(room);
            GamePortfolioSnapshot snapshot = GamePortfolioSnapshot.create(
                    turn, participantA, new BigDecimal("5000000"),
                    new BigDecimal("6500000"), SEED);  // total = 11,500,000

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(snapshotRepository.findLatestByParticipant(participantA))
                    .willReturn(Optional.of(snapshot));
            given(gameOrderRepository.countByParticipant(participantA)).willReturn(5);
            given(gameResultRepository.save(any(GameResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            // finish() 이전 ACTIVE 조회 → 본인 포함 전원 ACTIVE
            given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                    .willReturn(List.of(participantA, participantB));

            // When
            GameEndInfo result = gameEndService.endGame(ROOM_ID, USER_A);

            // Then
            assertThat(result.finalAsset()).isEqualByComparingTo(new BigDecimal("11500000"));
            assertThat(result.profitRate()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(result.totalTrades()).isEqualTo(5);
            assertThat(result.roomFinished()).isFalse();
            assertThat(participantA.getStatus()).isEqualTo(ParticipantStatus.FINISHED);
            assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("마지막 참가자 종료 — 방도 FINISHED + 순위 확정")
        void endGame_lastParticipant_roomFinished() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);

            GameTurn turn = GameTurn.createFirst(room);
            GamePortfolioSnapshot snapshot = GamePortfolioSnapshot.create(
                    turn, participantA, new BigDecimal("8000000"),
                    new BigDecimal("4000000"), SEED);  // total = 12,000,000

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(snapshotRepository.findLatestByParticipant(participantA))
                    .willReturn(Optional.of(snapshot));
            given(gameOrderRepository.countByParticipant(participantA)).willReturn(10);
            given(gameResultRepository.save(any(GameResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            // finish() 이전 ACTIVE 조회 → 본인만 ACTIVE (본인 제외하면 빈 리스트 → 방 종료)
            given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                    .willReturn(List.of(participantA));
            given(gameResultRepository.findByGameRoomOrderByFinalRankAsc(room))
                    .willReturn(List.of(GameResult.create(
                            room, participantA, 0, SEED,
                            new BigDecimal("12000000"), new BigDecimal("20.00"), 10)));

            // When
            GameEndInfo result = gameEndService.endGame(ROOM_ID, USER_A);

            // Then
            assertThat(result.roomFinished()).isTrue();
            assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
        }

        @Test
        @DisplayName("2명 중 1명 이미 FINISHED → 마지막 ACTIVE 종료 시 방 FINISHED + 순위 2명 확정")
        void endGame_secondParticipant_roomFinished_ranksBothConfirmed() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            participantB.finish(); // B는 이미 종료

            GameTurn turn = GameTurn.createFirst(room);
            GamePortfolioSnapshot snapshotA = GamePortfolioSnapshot.create(
                    turn, participantA, new BigDecimal("7000000"),
                    new BigDecimal("5000000"), SEED);  // A total = 12,000,000

            // B의 기존 결과 (개별 종료 시 저장됨, rank=0)
            GameResult resultB = GameResult.create(room, participantB, 0, SEED,
                    new BigDecimal("9000000"), new BigDecimal("-10.00"), 3);

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(snapshotRepository.findLatestByParticipant(participantA))
                    .willReturn(Optional.of(snapshotA));
            given(gameOrderRepository.countByParticipant(participantA)).willReturn(7);
            given(gameResultRepository.save(any(GameResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            // finish() 이전 ACTIVE 조회 → A만 ACTIVE (B는 이미 FINISHED)
            given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                    .willReturn(List.of(participantA));
            // 순위 확정 시 A + B 결과 모두 조회
            GameResult resultA = GameResult.create(room, participantA, 0, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 7);
            given(gameResultRepository.findByGameRoomOrderByFinalRankAsc(room))
                    .willReturn(List.of(resultA, resultB));

            // When
            GameEndInfo result = gameEndService.endGame(ROOM_ID, USER_A);

            // Then
            assertThat(result.roomFinished()).isTrue();
            assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
            // A: 12,000,000 > B: 9,000,000 → A가 1위, B가 2위
            assertThat(resultA.getFinalRank()).isEqualTo(1);
            assertThat(resultB.getFinalRank()).isEqualTo(2);
        }

        @Test
        @DisplayName("스냅샷 없이 종료 — 시드머니 그대로")
        void endGame_noSnapshot_seedMoney() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(snapshotRepository.findLatestByParticipant(participantA))
                    .willReturn(Optional.empty());
            given(gameOrderRepository.countByParticipant(participantA)).willReturn(0);
            given(gameResultRepository.save(any(GameResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            // finish() 이전 ACTIVE 조회 → 본인 + B 둘 다 ACTIVE
            given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                    .willReturn(List.of(participantA, GameParticipant.createMember(room, USER_B)));

            // When
            GameEndInfo result = gameEndService.endGame(ROOM_ID, USER_A);

            // Then
            assertThat(result.finalAsset()).isEqualByComparingTo(SEED);
            assertThat(result.profitRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalTrades()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("개별 게임 종료 실패")
    class FailTests {

        @Test
        @DisplayName("방이 없으면 ROOM_NOT_FOUND")
        void roomNotFound() {
            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 종료된 방이면 GAME_ALREADY_FINISHED")
        void gameAlreadyFinished() {
            GameRoom room = createStartedRoom();
            room.finish();
            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_ALREADY_FINISHED);
        }

        @Test
        @DisplayName("게임 미시작이면 GAME_NOT_STARTED")
        void gameNotStarted() {
            GameRoom room = createWaitingRoom();
            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }

        @Test
        @DisplayName("참가자가 아니면 ROOM_NOT_PARTICIPANT")
        void notParticipant() {
            GameRoom room = createStartedRoom();
            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("이미 FINISHED면 PARTICIPANT_ALREADY_FINISHED")
        void alreadyFinished() {
            GameRoom room = createStartedRoom();
            GameParticipant participant = GameParticipant.createLeader(room, USER_A);
            participant.finish();

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participant));

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_ALREADY_FINISHED);

            verify(gameResultRepository, never()).save(any());
        }

        @Test
        @DisplayName("LEFT 상태 참가자가 종료 시도하면 ROOM_NOT_PARTICIPANT")
        void leftParticipantCannotEnd() {
            GameRoom room = createStartedRoom();
            GameParticipant participant = GameParticipant.createLeader(room, USER_A);
            participant.leave();

            given(gameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participant));

            assertThatThrownBy(() -> gameEndService.endGame(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);

            verify(gameResultRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("순위 확정 (finalizeRanks)")
    class FinalizeRanksTests {

        @Test
        @DisplayName("3명 중 2명 동률 → 1위, 1위, 3위")
        void tieBreaking() {
            GameRoom room = createStartedRoom();
            GameParticipant pA = GameParticipant.createLeader(room, USER_A);
            GameParticipant pB = GameParticipant.createMember(room, USER_B);
            UUID userC = UUID.fromString("00000000-0000-4000-a000-000000000004");
            GameParticipant pC = GameParticipant.createMember(room, userC);

            GameResult rA = GameResult.create(room, pA, 0, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 5);
            GameResult rB = GameResult.create(room, pB, 0, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 3);
            GameResult rC = GameResult.create(room, pC, 0, SEED,
                    new BigDecimal("9000000"), new BigDecimal("-10.00"), 2);

            given(gameResultRepository.findByGameRoomOrderByFinalRankAsc(room))
                    .willReturn(List.of(rA, rB, rC));

            gameEndService.finalizeRanks(room);

            assertThat(rA.getFinalRank()).isEqualTo(1);
            assertThat(rB.getFinalRank()).isEqualTo(1);
            assertThat(rC.getFinalRank()).isEqualTo(3);
        }

        @Test
        @DisplayName("전원 동률 → 모두 1위")
        void allTied() {
            GameRoom room = createStartedRoom();
            GameParticipant pA = GameParticipant.createLeader(room, USER_A);
            GameParticipant pB = GameParticipant.createMember(room, USER_B);

            GameResult rA = GameResult.create(room, pA, 0, SEED,
                    new BigDecimal("10000000"), BigDecimal.ZERO, 0);
            GameResult rB = GameResult.create(room, pB, 0, SEED,
                    new BigDecimal("10000000"), BigDecimal.ZERO, 0);

            given(gameResultRepository.findByGameRoomOrderByFinalRankAsc(room))
                    .willReturn(List.of(rA, rB));

            gameEndService.finalizeRanks(room);

            assertThat(rA.getFinalRank()).isEqualTo(1);
            assertThat(rB.getFinalRank()).isEqualTo(1);
        }
    }

    // === 헬퍼 메서드 ===

    private GameRoom createStartedRoom() {
        GameRoom room = GameRoom.create(GROUP_ID, USER_A, SEED,
                6, 7, START_DATE, START_DATE.plusMonths(6));
        room.start();
        return room;
    }

    private GameRoom createWaitingRoom() {
        return GameRoom.create(GROUP_ID, USER_A, SEED,
                6, 7, START_DATE, START_DATE.plusMonths(6));
    }
}
