package com.solv.wefin.domain.game.turn.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameTurnServiceTest {

    @InjectMocks
    private GameTurnService gameTurnService;

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private GameTurnRepository gameTurnRepository;

    private static final UUID TEST_ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final Long TEST_GROUP_ID = 1L;
    private static final LocalDate TEST_START_DATE = LocalDate.of(2022, 3, 2);

    // === 성공 케이스 ===

    @Test
    @DisplayName("현재 턴 조회 성공 — 활성 턴 반환")
    void getCurrentTurn_success() {
        // Given
        GameRoom room = createGameRoom();
        GameTurn turn = GameTurn.createFirst(room);
        GameParticipant participant = GameParticipant.createLeader(room, TEST_USER_ID);

        given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
        given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                .willReturn(Optional.of(turn));

        // When
        GameTurn result = gameTurnService.getCurrentTurn(TEST_ROOM_ID, TEST_USER_ID);

        // Then
        assertThat(result).isEqualTo(turn);
        assertThat(result.getTurnNumber()).isEqualTo(1);
        assertThat(result.getTurnDate()).isEqualTo(TEST_START_DATE);
        assertThat(result.getStatus()).isEqualTo(TurnStatus.ACTIVE);
    }

    // === 실패 케이스 ===

    @Nested
    @DisplayName("검증 실패")
    class ValidationTests {

        @Test
        @DisplayName("실패 — 게임방이 존재하지 않음")
        void fail_roomNotFound() {
            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> gameTurnService.getCurrentTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 — 참가자가 아님")
        void fail_notParticipant() {
            GameRoom room = createGameRoom();
            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> gameTurnService.getCurrentTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("실패 — 참가자가 비활성 상태 (LEFT)")
        void fail_participantNotActive() {
            GameRoom room = createGameRoom();
            GameParticipant participant = GameParticipant.createLeader(room, TEST_USER_ID);
            participant.leave();  // status → LEFT

            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));

            assertThatThrownBy(() -> gameTurnService.getCurrentTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("실패 — 활성 턴이 없음 (게임 미시작)")
        void fail_noActiveTurn() {
            GameRoom room = createGameRoom();
            GameParticipant participant = GameParticipant.createLeader(room, TEST_USER_ID);

            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));
            given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> gameTurnService.getCurrentTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }
    }

    // === 헬퍼 메서드 ===

    private GameRoom createGameRoom() {
        GameRoom room = GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, new BigDecimal("10000000"),
                6, 7, TEST_START_DATE, TEST_START_DATE.plusMonths(6));
        room.start();
        return room;
    }
}
