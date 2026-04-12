package com.solv.wefin.domain.game.news.service;

import com.solv.wefin.domain.game.news.dto.BriefingInfo;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameBriefingServiceTest {

    @InjectMocks
    private GameBriefingService gameBriefingService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameParticipantRepository gameParticipantRepository;

    @Mock
    private GameTurnRepository gameTurnRepository;

    @Mock
    private BriefingService briefingService;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final Long TEST_GROUP_ID = 1L;
    private static final String BRIEFING_TEXT = "2022-01-03 시장 브리핑: 코스피 상승 마감.";

    // === 성공 케이스 ===

    @Test
    @DisplayName("브리핑 조회 성공 — 활성 턴의 날짜로 브리핑 반환")
    void getBriefingForRoom_success() {
        // Given — 방 존재, ACTIVE 참가자, ACTIVE 턴, 브리핑 텍스트
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);
        LocalDate turnDate = activeTurn.getTurnDate();

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(briefingService.getBriefingForDate(turnDate))
                .willReturn(BRIEFING_TEXT);

        // When
        BriefingInfo result = gameBriefingService.getBriefingForRoom(roomId, TEST_USER_ID);

        // Then — 활성 턴의 날짜와 브리핑 텍스트가 묶여서 반환
        assertThat(result).isNotNull();
        assertThat(result.targetDate()).isEqualTo(turnDate);
        assertThat(result.briefingText()).isEqualTo(BRIEFING_TEXT);

        verify(briefingService).getBriefingForDate(turnDate);
    }

    // === 실패 케이스 ===

    @Test
    @DisplayName("브리핑 조회 실패 — 존재하지 않는 방이면 ROOM_NOT_FOUND")
    void getBriefingForRoom_roomNotFound() {
        // Given
        UUID fakeRoomId = UUID.fromString("00000000-0000-4000-a000-999999999999");
        given(gameRoomRepository.findById(fakeRoomId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gameBriefingService.getBriefingForRoom(fakeRoomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
                });

        // 방이 없으면 이후 단계는 호출되지 않아야 한다
        verify(gameParticipantRepository, never()).findByGameRoomAndUserId(any(), any());
        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(briefingService, never()).getBriefingForDate(any());
    }

    @Test
    @DisplayName("브리핑 조회 실패 — 참가자가 아니면 ROOM_NOT_PARTICIPANT")
    void getBriefingForRoom_notParticipant() {
        // Given — 방은 존재하지만 해당 유저는 참가자가 아님
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        UUID outsiderUserId = UUID.fromString("00000000-0000-4000-a000-000000000099");

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, outsiderUserId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gameBriefingService.getBriefingForRoom(roomId, outsiderUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_PARTICIPANT);
                });

        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(briefingService, never()).getBriefingForDate(any());
    }

    @Test
    @DisplayName("브리핑 조회 실패 — 이미 퇴장(LEFT)한 참가자면 ROOM_NOT_PARTICIPANT")
    void getBriefingForRoom_participantLeft() {
        // Given — 참가자 row는 있지만 status가 LEFT
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant leftParticipant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        leftParticipant.leave(); // status → LEFT

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(leftParticipant));

        // When & Then
        assertThatThrownBy(() -> gameBriefingService.getBriefingForRoom(roomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_PARTICIPANT);
                });

        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(briefingService, never()).getBriefingForDate(any());
    }

    @Test
    @DisplayName("브리핑 조회 실패 — ACTIVE 턴이 없으면 GAME_NOT_STARTED")
    void getBriefingForRoom_gameNotStarted() {
        // Given — 방, 참가자는 정상이지만 ACTIVE 턴이 없음 (게임 아직 시작 전 or 이미 종료)
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gameBriefingService.getBriefingForRoom(roomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.GAME_NOT_STARTED);
                });

        // ACTIVE 턴이 없으면 하위 BriefingService는 호출되지 않아야 한다
        verify(briefingService, never()).getBriefingForDate(any());
    }

    // === 헬퍼 메서드 ===

    private GameRoom createGameRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, new BigDecimal("10000000"),
                6, 7, LocalDate.of(2022, 1, 3), LocalDate.of(2022, 7, 3));
    }
}
