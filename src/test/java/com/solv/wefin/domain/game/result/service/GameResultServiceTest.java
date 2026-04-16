package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GameResultServiceTest {

    @InjectMocks
    private GameResultService gameResultService;

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private GameResultRepository gameResultRepository;
    @Mock
    private UserRepository userRepository;

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-a000-000000000003");
    private static final UUID USER_C = UUID.fromString("00000000-0000-4000-a000-000000000004");
    private static final Long GROUP_ID = 1L;
    private static final LocalDate START_DATE = LocalDate.of(2022, 3, 2);
    private static final LocalDate END_DATE = LocalDate.of(2022, 9, 2);
    private static final BigDecimal SEED = new BigDecimal("10000000");

    // === 성공 테스트 ===

    @Nested
    @DisplayName("게임 결과 조회 성공")
    class SuccessTests {

        @Test
        @DisplayName("FINISHED 참가자 조회 — finalAsset DESC 순서로 순위 부여")
        void getResult_sortedByFinalAssetDesc() {
            // Given
            GameRoom room = createFinishedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            GameParticipant participantC = GameParticipant.createMember(room, USER_C);
            participantA.finish();
            participantB.finish();
            participantC.finish();

            // DB는 finalAsset DESC로 정렬된 리스트를 반환 (B > C > A)
            GameResult resultB = GameResult.create(room, participantB, 0, SEED,
                    new BigDecimal("12500000"), new BigDecimal("25.00"), 42);
            GameResult resultC = GameResult.create(room, participantC, 0, SEED,
                    new BigDecimal("10500000"), new BigDecimal("5.00"), 15);
            GameResult resultA = GameResult.create(room, participantA, 0, SEED,
                    new BigDecimal("9000000"), new BigDecimal("-10.00"), 8);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameResultRepository.findByGameRoomOrderByFinalAssetDescCreatedAtAsc(room))
                    .willReturn(List.of(resultB, resultC, resultA));

            User userA = mockUser(USER_A, "재훈");
            User userB = mockUser(USER_B, "길동");
            User userC = mockUser(USER_C, "철수");
            given(userRepository.findAllById(any()))
                    .willReturn(List.of(userA, userB, userC));

            // When
            GameResultInfo info = gameResultService.getGameResult(ROOM_ID, USER_A);

            // Then
            assertThat(info.startDate()).isEqualTo(START_DATE);
            assertThat(info.endDate()).isEqualTo(END_DATE);
            assertThat(info.rankings()).hasSize(3);

            // 1위: B
            assertThat(info.rankings().get(0).rank()).isEqualTo(1);
            assertThat(info.rankings().get(0).userName()).isEqualTo("길동");
            assertThat(info.rankings().get(0).finalAsset()).isEqualByComparingTo("12500000");
            assertThat(info.rankings().get(0).profitRate()).isEqualByComparingTo("25.00");
            assertThat(info.rankings().get(0).totalTrades()).isEqualTo(42);

            // 2위: C
            assertThat(info.rankings().get(1).rank()).isEqualTo(2);
            assertThat(info.rankings().get(1).userName()).isEqualTo("철수");

            // 3위: A
            assertThat(info.rankings().get(2).rank()).isEqualTo(3);
            assertThat(info.rankings().get(2).userName()).isEqualTo("재훈");
        }

        @Test
        @DisplayName("동률 처리 — 같은 finalAsset이면 같은 순위 (1위, 1위, 3위)")
        void tieBreaking_sameFinalAsset_sameRank() {
            // Given
            GameRoom room = createFinishedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            GameParticipant participantC = GameParticipant.createMember(room, USER_C);
            participantA.finish();
            participantB.finish();
            participantC.finish();

            // A, B 동률 / C 낮음
            GameResult resultA = GameResult.create(room, participantA, 0, SEED,
                    new BigDecimal("11000000"), new BigDecimal("10.00"), 20);
            GameResult resultB = GameResult.create(room, participantB, 0, SEED,
                    new BigDecimal("11000000"), new BigDecimal("10.00"), 18);
            GameResult resultC = GameResult.create(room, participantC, 0, SEED,
                    new BigDecimal("8000000"), new BigDecimal("-20.00"), 5);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameResultRepository.findByGameRoomOrderByFinalAssetDescCreatedAtAsc(room))
                    .willReturn(List.of(resultA, resultB, resultC));

            User userA = mockUser(USER_A, "재훈");
            User userB = mockUser(USER_B, "길동");
            User userC = mockUser(USER_C, "철수");
            given(userRepository.findAllById(any()))
                    .willReturn(List.of(userA, userB, userC));

            // When
            GameResultInfo info = gameResultService.getGameResult(ROOM_ID, USER_A);

            // Then
            assertThat(info.rankings()).hasSize(3);
            assertThat(info.rankings().get(0).rank()).isEqualTo(1);
            assertThat(info.rankings().get(1).rank()).isEqualTo(1);  // 동률
            assertThat(info.rankings().get(2).rank()).isEqualTo(3);  // 1위가 2명 → 다음은 3위
        }

        @Test
        @DisplayName("방이 아직 IN_PROGRESS여도 본인 FINISHED면 조회 가능")
        void inProgressRoom_finishedParticipant_canQuery() {
            // Given — 방은 IN_PROGRESS, A만 FINISHED
            GameRoom room = createStartedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            participantA.finish();

            GameResult resultA = GameResult.create(room, participantA, 0, SEED,
                    new BigDecimal("11000000"), new BigDecimal("10.00"), 12);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameResultRepository.findByGameRoomOrderByFinalAssetDescCreatedAtAsc(room))
                    .willReturn(List.of(resultA));

            User userA = mockUser(USER_A, "재훈");
            given(userRepository.findAllById(any())).willReturn(List.of(userA));

            // When
            GameResultInfo info = gameResultService.getGameResult(ROOM_ID, USER_A);

            // Then
            assertThat(info.rankings()).hasSize(1);
            assertThat(info.rankings().get(0).rank()).isEqualTo(1);
            assertThat(info.rankings().get(0).userName()).isEqualTo("재훈");
        }

        @Test
        @DisplayName("닉네임 조회 누락 — 탈퇴 등으로 user 레코드 없으면 '알 수 없음' fallback")
        void missingNickname_fallsBackToUnknown() {
            // Given — game_result에는 USER_B 결과가 있지만 user 테이블에서 USER_B 누락 (탈퇴 가정)
            GameRoom room = createFinishedRoom();
            GameParticipant participantA = GameParticipant.createLeader(room, USER_A);
            GameParticipant participantB = GameParticipant.createMember(room, USER_B);
            participantA.finish();
            participantB.finish();

            GameResult resultA = GameResult.create(room, participantA, 0, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 10);
            GameResult resultB = GameResult.create(room, participantB, 0, SEED,
                    new BigDecimal("9000000"), new BigDecimal("-10.00"), 5);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participantA));
            given(gameResultRepository.findByGameRoomOrderByFinalAssetDescCreatedAtAsc(room))
                    .willReturn(List.of(resultA, resultB));

            // USER_A만 user 테이블에 존재, USER_B는 누락
            User userA = mockUser(USER_A, "재훈");
            given(userRepository.findAllById(any())).willReturn(List.of(userA));

            // When
            GameResultInfo info = gameResultService.getGameResult(ROOM_ID, USER_A);

            // Then
            assertThat(info.rankings()).hasSize(2);
            assertThat(info.rankings().get(0).userName()).isEqualTo("재훈");
            assertThat(info.rankings().get(1).userName()).isEqualTo("알 수 없음");
        }
    }

    // === 실패 테스트 ===

    @Nested
    @DisplayName("게임 결과 조회 실패")
    class FailTests {

        @Test
        @DisplayName("방이 없으면 ROOM_NOT_FOUND")
        void roomNotFound() {
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> gameResultService.getGameResult(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("참가자가 아니면 PARTICIPANT_NOT_FINISHED")
        void notParticipant() {
            GameRoom room = createFinishedRoom();
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> gameResultService.getGameResult(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);
        }

        @Test
        @DisplayName("참가자가 ACTIVE면 PARTICIPANT_NOT_FINISHED")
        void participantActive() {
            GameRoom room = createStartedRoom();
            GameParticipant participant = GameParticipant.createLeader(room, USER_A);
            // status 그대로 ACTIVE

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participant));

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            assertThatThrownBy(() -> gameResultService.getGameResult(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);
        }

        @Test
        @DisplayName("참가자가 LEFT면 PARTICIPANT_NOT_FINISHED")
        void participantLeft() {
            GameRoom room = createStartedRoom();
            GameParticipant participant = GameParticipant.createLeader(room, USER_A);
            participant.leave();

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_A))
                    .willReturn(Optional.of(participant));

            assertThatThrownBy(() -> gameResultService.getGameResult(ROOM_ID, USER_A))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);
        }
    }

    // === 헬퍼 메서드 ===

    private GameRoom createStartedRoom() {
        GameRoom room = GameRoom.create(GROUP_ID, USER_A, SEED,
                6, 7, START_DATE, END_DATE);
        room.start();
        return room;
    }

    private GameRoom createFinishedRoom() {
        GameRoom room = createStartedRoom();
        room.finish();
        return room;
    }

    private User mockUser(UUID userId, String nickname) {
        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);
        given(user.getNickname()).willReturn(nickname);
        return user;
    }
}
