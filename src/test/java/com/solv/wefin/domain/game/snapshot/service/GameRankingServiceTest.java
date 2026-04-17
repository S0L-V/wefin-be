package com.solv.wefin.domain.game.snapshot.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.dto.RankingInfo;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GameRankingServiceTest {

    @InjectMocks
    private GameRankingService rankingService;

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private GameTurnRepository gameTurnRepository;
    @Mock
    private GamePortfolioSnapshotRepository snapshotRepository;
    @Mock
    private UserRepository userRepository;

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-a000-000000000003");
    private static final UUID USER_C = UUID.fromString("00000000-0000-4000-a000-000000000004");
    private static final Long GROUP_ID = 1L;
    private static final LocalDate START_DATE = LocalDate.of(2022, 3, 2);
    private static final BigDecimal SEED = new BigDecimal("10000000");

    // === 성공 테스트 ===

    @Nested
    @DisplayName("랭킹 조회 성공")
    class SuccessTests {

        @Test
        @DisplayName("첫 턴 — 스냅샷 없음, 전원 동률")
        void firstTurn_allEqual() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameTurnRepository.findFirstByGameRoomAndStatusOrderByTurnNumberDesc(room, TurnStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(gameParticipantRepository.findByGameRoomAndStatusIn(room, List.of(ParticipantStatus.ACTIVE, ParticipantStatus.FINISHED)))
                    .willReturn(List.of(participantA, participantB));

            User userA = mockUser(USER_A, "재훈");
            User userB = mockUser(USER_B, "길동");
            given(userRepository.findAllById(List.of(USER_A, USER_B)))
                    .willReturn(List.of(userA, userB));

            // When
            List<RankingInfo> rankings = rankingService.getRankings(ROOM_ID, USER_A);

            // Then
            assertThat(rankings).hasSize(2);
            assertThat(rankings).allSatisfy(r -> {
                assertThat(r.rank()).isEqualTo(1);
                assertThat(r.totalAsset()).isEqualByComparingTo(SEED);
                assertThat(r.profitRate()).isEqualByComparingTo(BigDecimal.ZERO);
            });
        }

        @Test
        @DisplayName("턴 전환 후 — 스냅샷 기준 순위 부여")
        void afterTurnAdvance_rankedByTotalAsset() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            GameParticipant participantC = GameParticipant.createMember(room, USER_C);

            GameTurn completedTurn = GameTurn.createFirst(room);

            // 스냅샷: B > C > A 순서 (totalAsset DESC로 정렬된 상태)
            GamePortfolioSnapshot snapB = GamePortfolioSnapshot.create(
                    completedTurn, participantB, new BigDecimal("5000000"),
                    new BigDecimal("6500000"), SEED);
            GamePortfolioSnapshot snapC = GamePortfolioSnapshot.create(
                    completedTurn, participantC, new BigDecimal("7000000"),
                    new BigDecimal("3000000"), SEED);
            GamePortfolioSnapshot snapA = GamePortfolioSnapshot.create(
                    completedTurn, participantA, new BigDecimal("8000000"),
                    new BigDecimal("1000000"), SEED);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameTurnRepository.findFirstByGameRoomAndStatusOrderByTurnNumberDesc(room, TurnStatus.COMPLETED))
                    .willReturn(Optional.of(completedTurn));
            given(snapshotRepository.findByTurnOrderByTotalAssetDesc(completedTurn))
                    .willReturn(List.of(snapB, snapC, snapA));

            User userA = mockUser(USER_A, "재훈");
            User userB = mockUser(USER_B, "길동");
            User userC = mockUser(USER_C, "철수");
            given(userRepository.findAllById(any()))
                    .willReturn(List.of(userA, userB, userC));

            // When
            List<RankingInfo> rankings = rankingService.getRankings(ROOM_ID, USER_A);

            // Then
            assertThat(rankings).hasSize(3);

            // 1위: B (totalAsset = 11,500,000)
            assertThat(rankings.get(0).rank()).isEqualTo(1);
            assertThat(rankings.get(0).userName()).isEqualTo("길동");
            assertThat(rankings.get(0).totalAsset()).isEqualByComparingTo(new BigDecimal("11500000"));

            // 2위: C (totalAsset = 10,000,000)
            assertThat(rankings.get(1).rank()).isEqualTo(2);
            assertThat(rankings.get(1).userName()).isEqualTo("철수");

            // 3위: A (totalAsset = 9,000,000)
            assertThat(rankings.get(2).rank()).isEqualTo(3);
            assertThat(rankings.get(2).userName()).isEqualTo("재훈");
        }

        @Test
        @DisplayName("동률 처리 — 같은 totalAsset이면 같은 순위 (1위, 1위, 3위)")
        void tieBreaking_sameTotalAsset_sameRank() {
            // Given
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            GameParticipant participantC = GameParticipant.createMember(room, USER_C);

            GameTurn completedTurn = GameTurn.createFirst(room);

            // A와 B가 동일 totalAsset, C는 낮음
            // create(turn, participant, stockValue, cash, seed)
            // totalAsset = stockValue + cash
            GamePortfolioSnapshot snapA = GamePortfolioSnapshot.create(
                    completedTurn, participantA, new BigDecimal("6000000"),
                    new BigDecimal("5000000"), SEED);  // total = 11,000,000
            GamePortfolioSnapshot snapB = GamePortfolioSnapshot.create(
                    completedTurn, participantB, new BigDecimal("4000000"),
                    new BigDecimal("7000000"), SEED);  // total = 11,000,000
            GamePortfolioSnapshot snapC = GamePortfolioSnapshot.create(
                    completedTurn, participantC, new BigDecimal("3000000"),
                    new BigDecimal("5000000"), SEED);  // total = 8,000,000

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameTurnRepository.findFirstByGameRoomAndStatusOrderByTurnNumberDesc(room, TurnStatus.COMPLETED))
                    .willReturn(Optional.of(completedTurn));
            given(snapshotRepository.findByTurnOrderByTotalAssetDesc(completedTurn))
                    .willReturn(List.of(snapA, snapB, snapC));  // DESC 정렬: A=B > C

            User userA = mockUser(USER_A, "재훈");
            User userB = mockUser(USER_B, "길동");
            User userC = mockUser(USER_C, "철수");
            given(userRepository.findAllById(any()))
                    .willReturn(List.of(userA, userB, userC));

            // When
            List<RankingInfo> rankings = rankingService.getRankings(ROOM_ID, USER_A);

            // Then
            assertThat(rankings).hasSize(3);

            // 1위: A (totalAsset = 11,000,000)
            assertThat(rankings.get(0).rank()).isEqualTo(1);
            assertThat(rankings.get(0).totalAsset()).isEqualByComparingTo(new BigDecimal("11000000"));

            // 1위: B (totalAsset = 11,000,000 — 동률)
            assertThat(rankings.get(1).rank()).isEqualTo(1);
            assertThat(rankings.get(1).totalAsset()).isEqualByComparingTo(new BigDecimal("11000000"));

            // 3위: C (totalAsset = 8,000,000 — 1위가 2명이므로 3위)
            assertThat(rankings.get(2).rank()).isEqualTo(3);
            assertThat(rankings.get(2).totalAsset()).isEqualByComparingTo(new BigDecimal("8000000"));
        }

        @Test
        @DisplayName("게임 종료(FINISHED) 상태에서도 랭킹 조회 가능")
        void finishedRoom_canQueryRankings() {
            // Given
            GameRoom room = createStartedRoom();
            room.finish();  // FINISHED 상태
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);

            GameTurn completedTurn = GameTurn.createFirst(room);
            GamePortfolioSnapshot snapA = GamePortfolioSnapshot.create(
                    completedTurn, participantA, new BigDecimal("5000000"),
                    new BigDecimal("5000000"), SEED);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameTurnRepository.findFirstByGameRoomAndStatusOrderByTurnNumberDesc(room, TurnStatus.COMPLETED))
                    .willReturn(Optional.of(completedTurn));
            given(snapshotRepository.findByTurnOrderByTotalAssetDesc(completedTurn))
                    .willReturn(List.of(snapA));

            User userA = mockUser(USER_A, "재훈");
            given(userRepository.findAllById(any())).willReturn(List.of(userA));

            // When
            List<RankingInfo> rankings = rankingService.getRankings(ROOM_ID, USER_A);

            // Then
            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).rank()).isEqualTo(1);
            assertThat(rankings.get(0).userName()).isEqualTo("재훈");
        }
    }

    // === 실패 테스트 ===

    @Nested
    @DisplayName("랭킹 조회 실패")
    class FailTests {

        @Test
        @DisplayName("방이 없으면 ROOM_NOT_FOUND")
        void roomNotFound() {
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> rankingService.getRankings(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("게임 미시작이면 GAME_NOT_STARTED")
        void gameNotStarted() {
            GameRoom room = createWaitingRoom();
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> rankingService.getRankings(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }

        @Test
        @DisplayName("참가자가 아니면 ROOM_NOT_PARTICIPANT")
        void notParticipant() {
            GameRoom room = createStartedRoom();
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> rankingService.getRankings(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
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

    private User mockUser(UUID userId, String nickname) {
        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);
        given(user.getNickname()).willReturn(nickname);
        return user;
    }
}
