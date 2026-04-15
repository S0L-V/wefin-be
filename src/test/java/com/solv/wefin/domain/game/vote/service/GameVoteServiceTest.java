package com.solv.wefin.domain.game.vote.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.turn.service.TurnAdvanceService;
import com.solv.wefin.domain.game.vote.VoteBroadcaster;
import com.solv.wefin.domain.game.vote.VoteSession;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameVoteServiceTest {

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private TurnAdvanceService turnAdvanceService;
    @Mock
    private VoteBroadcaster voteBroadcaster;
    @Mock
    private UserRepository userRepository;

    private ScheduledExecutorService voteScheduler;
    private GameVoteService gameVoteService;

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID HOST_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-a000-000000000003");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-a000-000000000004");
    private static final Long GROUP_ID = 1L;

    @BeforeEach
    void setUp() {
        voteScheduler = Executors.newSingleThreadScheduledExecutor();
        gameVoteService = new GameVoteService(
                gameRoomRepository, gameParticipantRepository,
                turnAdvanceService, voteBroadcaster, voteScheduler, userRepository);
    }

    @AfterEach
    void tearDown() {
        voteScheduler.shutdownNow();
    }

    private GameRoom createRoom(RoomStatus status) {
        GameRoom room = GameRoom.create(
                GROUP_ID, HOST_ID, new BigDecimal("10000000"),
                12, 7, LocalDate.of(2022, 1, 3), LocalDate.of(2023, 1, 3));
        if (status == RoomStatus.IN_PROGRESS) {
            room.start();
        }
        return room;
    }

    private void setupCommonMocks(GameRoom room, UUID userId) {
        setupCommonMocks(room, userId, false);
    }

    private void setupCommonMocks(GameRoom room, UUID userId, boolean isLeader) {
        given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        GameParticipant participant = isLeader
                ? GameParticipant.createLeader(room, userId)
                : GameParticipant.createMember(room, userId);
        given(gameParticipantRepository.findByGameRoomAndUserId(room, userId))
                .willReturn(Optional.of(participant));

        if (isLeader) {
            User mockUser = mock(User.class);
            lenient().when(mockUser.getNickname()).thenReturn("테스트방장");
            lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        }
    }

    // === 투표 시작 성공 ===

    @Nested
    @DisplayName("투표 시작 성공")
    class VoteStartSuccess {

        @Test
        @DisplayName("방장이 투표 시작 -- 세션 생성 + 찬성 1표")
        void host_starts_vote() {
            // Given
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);

            // When
            VoteSession session = gameVoteService.vote(ROOM_ID, HOST_ID, true);

            // Then
            assertThat(session).isNotNull();
            assertThat(session.getAgreeCount()).isEqualTo(1);
            assertThat(session.getTotalCount()).isEqualTo(3);
            assertThat(session.getInitiatorId()).isEqualTo(HOST_ID);

            verify(voteBroadcaster).broadcastStart(eq(ROOM_ID), eq("테스트방장"), eq(3), eq(15));
            verify(voteBroadcaster).broadcastUpdate(eq(ROOM_ID), eq(1L), eq(0L), eq(3));
        }
    }

    // === 투표 참여 성공 ===

    @Nested
    @DisplayName("투표 참여 성공")
    class VoteCastSuccess {

        @Test
        @DisplayName("참가자가 찬성 투표")
        void participant_votes_agree() {
            // Given -- 먼저 방장이 투표 시작
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true);

            // 참가자 mock 재설정
            setupCommonMocks(room, USER_A);

            // When
            VoteSession session = gameVoteService.vote(ROOM_ID, USER_A, true);

            // Then
            assertThat(session.getAgreeCount()).isEqualTo(2);
            assertThat(session.getDisagreeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("참가자가 반대 투표")
        void participant_votes_disagree() {
            // Given
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true);

            setupCommonMocks(room, USER_A);

            // When
            VoteSession session = gameVoteService.vote(ROOM_ID, USER_A, false);

            // Then
            assertThat(session.getAgreeCount()).isEqualTo(1);
            assertThat(session.getDisagreeCount()).isEqualTo(1);
        }
    }

    // === 과반수 도달 -> 턴 전환 ===

    @Nested
    @DisplayName("투표 판정")
    class VoteResolve {

        @Test
        @DisplayName("과반수 찬성 -- 턴 전환 호출")
        void majority_reached_advances_turn() {
            // Given -- 3명 중 2명 찬성이면 과반수
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true); // 찬성 1

            setupCommonMocks(room, USER_A);

            // When -- 찬성 2 -> 과반수 도달
            gameVoteService.vote(ROOM_ID, USER_A, true);

            // Then
            verify(voteBroadcaster).broadcastResult(eq(ROOM_ID), eq(true), eq(2L), eq(0L));
            verify(turnAdvanceService).advanceTurn(ROOM_ID, HOST_ID);
        }

        @Test
        @DisplayName("부결 확정 -- 턴 전환 안 함")
        void rejection_certain_no_advance() {
            // Given -- 3명 중 과반수 2명 필요. 방장은 무조건 찬성(1)
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true); // 찬성 1

            setupCommonMocks(room, USER_A);
            gameVoteService.vote(ROOM_ID, USER_A, false); // 반대 1

            setupCommonMocks(room, USER_B);

            // When -- 반대 2 -> 부결 확정 (찬성 1, 반대 2이므로 과반수 불가)
            gameVoteService.vote(ROOM_ID, USER_B, false);

            // Then
            verify(voteBroadcaster).broadcastResult(eq(ROOM_ID), eq(false), eq(1L), eq(2L));
            verify(turnAdvanceService, never()).advanceTurn(any(), any());
        }
    }

    // === 실패 케이스 ===

    @Nested
    @DisplayName("투표 실패")
    class VoteFail {

        @Test
        @DisplayName("방이 없으면 ROOM_NOT_FOUND")
        void room_not_found() {
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, HOST_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("게임 미시작이면 GAME_NOT_STARTED")
        void game_not_started() {
            GameRoom room = createRoom(RoomStatus.WAITING);
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, HOST_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }

        @Test
        @DisplayName("참가자가 아니면 ROOM_NOT_PARTICIPANT")
        void not_participant() {
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, HOST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, HOST_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("방장 아닌 사람이 투표 시작 시 ROOM_NOT_HOST")
        void non_host_starts_vote() {
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, USER_A);

            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, USER_A, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_HOST);
        }

        @Test
        @DisplayName("중복 투표 시 VOTE_ALREADY_CAST")
        void duplicate_vote() {
            // Given
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(4);
            gameVoteService.vote(ROOM_ID, HOST_ID, true);

            // 같은 사용자가 다시 투표
            setupCommonMocks(room, HOST_ID, true);

            // When & Then
            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, HOST_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_ALREADY_CAST);
        }

        @Test
        @DisplayName("완료된 투표에 투표 시도 시 ROOM_NOT_HOST")
        void vote_after_completed() {
            // Given -- 3명 방에서 과반수 2명, 방장 찬성 + USER_A 찬성 -> 종료
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true);

            setupCommonMocks(room, USER_A);
            gameVoteService.vote(ROOM_ID, USER_A, true); // 2/3 과반수 -> 종료

            // 다른 참가자가 뒤늦게 투표 시도
            setupCommonMocks(room, USER_B);

            // When & Then -- 세션 제거됨 -> 새 투표 시작 시도 -> 방장 아님
            assertThatThrownBy(() -> gameVoteService.vote(ROOM_ID, USER_B, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_HOST);
        }
    }

    // === 방장 찬성 강제 ===

    @Nested
    @DisplayName("방장 투표 시작")
    class HostVoteStart {

        @Test
        @DisplayName("방장이 agree:false로 시작해도 찬성으로 기록됨")
        void host_agree_forced_true() {
            // Given
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(4);

            // When -- agree: false로 보내도
            VoteSession session = gameVoteService.vote(ROOM_ID, HOST_ID, false);

            // Then -- 찬성으로 기록
            assertThat(session.getAgreeCount()).isEqualTo(1);
            assertThat(session.getDisagreeCount()).isEqualTo(0);
        }
    }

    // === 전원 투표 완료 ===

    @Nested
    @DisplayName("전원 투표 완료")
    class AllVoted {

        @Test
        @DisplayName("전원 투표 완료 -- 찬성 미달 시 부결")
        void all_voted_not_enough_agrees() {
            // Given -- 3명 중 과반수 2명 필요. 방장 찬성 1 + 반대 2 -> 전원 투표 완료, 부결
            GameRoom room = createRoom(RoomStatus.IN_PROGRESS);
            setupCommonMocks(room, HOST_ID, true);
            given(gameParticipantRepository.countByGameRoomAndStatus(
                    eq(room), eq(ParticipantStatus.ACTIVE))).willReturn(3);
            gameVoteService.vote(ROOM_ID, HOST_ID, true); // 찬성 1

            setupCommonMocks(room, USER_A);
            gameVoteService.vote(ROOM_ID, USER_A, false); // 반대 1

            setupCommonMocks(room, USER_B);
            gameVoteService.vote(ROOM_ID, USER_B, false); // 반대 2 -> 부결 확정 (2 > 3-2=1)

            // Then
            verify(voteBroadcaster).broadcastResult(eq(ROOM_ID), eq(false), eq(1L), eq(2L));
            verify(turnAdvanceService, never()).advanceTurn(any(), any());
        }
    }
}
