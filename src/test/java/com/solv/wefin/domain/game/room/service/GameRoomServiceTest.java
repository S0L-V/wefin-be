package com.solv.wefin.domain.game.room.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.CreateRoomResponse;
import com.solv.wefin.web.game.room.dto.response.JoinRoomResponse;
import com.solv.wefin.web.game.room.dto.response.RoomListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRoomServiceTest {

    @InjectMocks
    private GameRoomService gameRoomService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameParticipantRepository gameParticipantRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final Long TEST_GROUP_ID = 1L;

    @Test
    @DisplayName("게임방 생성 성공 — 정상적으로 방과 참가자가 저장된다")
    void createRoom_success() {
        // Given — 그룹에 활성 방 없음, 방장 활성 방 없음, 오늘 게임 시작 이력 없음
        given(gameRoomRepository.existsByGroupIdAndStatusIn(any(Long.class), any(List.class)))
                .willReturn(false);
        given(gameRoomRepository.existsByUserIdAndStatusIn(any(UUID.class), any(List.class)))
                .willReturn(false);
        given(gameRoomRepository.existsByUserIdAndStartedAtBetween(
                any(UUID.class), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .willReturn(false);

        CreateRoomRequest request = createRequest();

        // When — 방 생성 호출
        CreateRoomResponse response = gameRoomService.createRoom(TEST_USER_ID, TEST_GROUP_ID, request);

        // Then — 결과 검증
        assertThat(response.getStatus()).isEqualTo(RoomStatus.WAITING);

        // GameRoom이 저장됐는지 확인
        verify(gameRoomRepository).save(any(GameRoom.class));

        // GameParticipant(방장)가 저장됐는지 확인
        verify(gameParticipantRepository).save(any());
    }

    @Test
    @DisplayName("게임방 생성 실패 — 방장 1일 1회 제한 위반 시 예외 발생")
    void createRoom_dailyLimitExceeded() {
        // Given — 그룹/방장 활성 방 없음, 오늘 이미 게임 시작 이력 있음
        given(gameRoomRepository.existsByGroupIdAndStatusIn(any(Long.class), any(List.class)))
                .willReturn(false);
        given(gameRoomRepository.existsByUserIdAndStatusIn(any(UUID.class), any(List.class)))
                .willReturn(false);
        given(gameRoomRepository.existsByUserIdAndStartedAtBetween(
                any(UUID.class), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .willReturn(true);

        CreateRoomRequest request = createRequest();

        // When & Then — BusinessException이 발생해야 한다
        assertThatThrownBy(() ->
                gameRoomService.createRoom(TEST_USER_ID, TEST_GROUP_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_HOST_DAILY_LIMIT);
                });

        // 예외 발생했으니 save()는 호출되면 안 된다
        verify(gameRoomRepository, never()).save(any());
        verify(gameParticipantRepository, never()).save(any());
    }

    // === API 2: 게임방 목록 조회 테스트 ===

    @Test
    @DisplayName("게임방 목록 조회 — 활성방 + 내 완료방 조회")
    void getRooms() {
        // Given — 활성방 1개, 내 완료방 1개
        GameRoom activeRoom = createGameRoom();
        GameRoom finishedRoom = createGameRoom();
        given(gameRoomRepository.findByGroupIdAndStatusIn(eq(TEST_GROUP_ID), anyList()))
                .willReturn(List.of(activeRoom));
        given(gameRoomRepository.findFinishedRoomsByGroupIdAndUserId(TEST_GROUP_ID, TEST_USER_ID))
                .willReturn(List.of(finishedRoom));
        given(gameParticipantRepository.countByGameRoomAndStatus(any(GameRoom.class), eq(ParticipantStatus.ACTIVE)))
                .willReturn(1);

        // When
        List<RoomListResponse> result = gameRoomService.getRooms(TEST_GROUP_ID, TEST_USER_ID);

        // Then — 2개 반환 (활성 1 + 완료 1)
        assertThat(result).hasSize(2);

        verify(gameRoomRepository).findByGroupIdAndStatusIn(eq(TEST_GROUP_ID), anyList());
        verify(gameRoomRepository).findFinishedRoomsByGroupIdAndUserId(TEST_GROUP_ID, TEST_USER_ID);
    }

    @Test
    @DisplayName("게임방 목록 조회 — 결과 없으면 빈 리스트 반환")
    void getRooms_empty() {
        // Given — 활성방 없음, 완료방 없음
        given(gameRoomRepository.findByGroupIdAndStatusIn(eq(TEST_GROUP_ID), anyList()))
                .willReturn(Collections.emptyList());
        given(gameRoomRepository.findFinishedRoomsByGroupIdAndUserId(TEST_GROUP_ID, TEST_USER_ID))
                .willReturn(Collections.emptyList());

        // When
        List<RoomListResponse> result = gameRoomService.getRooms(TEST_GROUP_ID, TEST_USER_ID);

        // Then — 빈 리스트 (에러 아님)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("게임방 상세 조회 실패 — 존재하지 않는 방이면 ROOM_NOT_FOUND")
    void getRoomDetail_notFound() {
        // Given — 존재하지 않는 roomId
        UUID fakeRoomId = UUID.fromString("00000000-0000-4000-a000-999999999999");
        given(gameRoomRepository.findById(fakeRoomId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gameRoomService.getRoomDetail(fakeRoomId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
                });

        // 방을 못 찾았으니 참가자 조회는 호출되면 안 된다
        verify(gameParticipantRepository, never()).findByGameRoomOrderByJoinedAtAsc(any());
    }


    // === API 4: 게임방 참가 테스트 ===

    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");

    @Test
    @DisplayName("게임방 참가 성공 — WAITING 방에 신규 참가")
    void joinRoom_success_waiting() {
        // Given — WAITING 방, 참가 이력 없음, 인원 여유 있음
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, OTHER_USER_ID))
                .willReturn(Optional.empty()); // 이력 없음
        given(gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE))
                .willReturn(1);

        // When
        JoinRoomResponse response = gameRoomService.joinRoom(roomId, OTHER_USER_ID);

        // Then
        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.WAITING);
        verify(gameParticipantRepository).save(any(GameParticipant.class));
    }

    @Test
    @DisplayName("게임방 참가 성공 — IN_PROGRESS 방에도 참가 가능")
    void joinRoom_success_inProgress() {
        // Given — IN_PROGRESS 방, 참가 이력 없음
        GameRoom gameRoom = createGameRoom();
        gameRoom.start();
        UUID roomId = gameRoom.getRoomId();

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, OTHER_USER_ID))
                .willReturn(Optional.empty());
        given(gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE))
                .willReturn(3);

        // When
        JoinRoomResponse response = gameRoomService.joinRoom(roomId, OTHER_USER_ID);

        // Then
        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        verify(gameParticipantRepository).save(any(GameParticipant.class));
    }

    @Test
    @DisplayName("게임방 참가 성공 — 퇴장 후 재참가 (LEFT → ACTIVE)")
    void joinRoom_success_rejoin() {
        // Given — LEFT 상태의 기존 참가 이력
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant leftParticipant = GameParticipant.createMember(gameRoom, OTHER_USER_ID);
        leftParticipant.leave(); // ACTIVE → LEFT

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, OTHER_USER_ID))
                .willReturn(Optional.of(leftParticipant)); // LEFT 상태로 존재
        given(gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE))
                .willReturn(3);

        // When
        JoinRoomResponse response = gameRoomService.joinRoom(roomId, OTHER_USER_ID);

        // Then — 기존 레코드 재활용, save() 호출 안 함 (더티 체킹)
        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(leftParticipant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE); // LEFT → ACTIVE
        verify(gameParticipantRepository, never()).save(any()); // 더티 체킹이니까 save 안 부름
    }

    @Test
    @DisplayName("게임방 참가 실패 — 존재하지 않는 방")
    void joinRoom_notFound() {
        // Given
        UUID fakeRoomId = UUID.fromString("00000000-0000-4000-a000-999999999999");
        given(gameRoomRepository.findByIdForUpdate(fakeRoomId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gameRoomService.joinRoom(fakeRoomId, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
                });

        verify(gameParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("게임방 참가 실패 — 종료된 방")
    void joinRoom_finished() {
        // Given — FINISHED 방
        GameRoom gameRoom = createGameRoom();
        gameRoom.start();
        gameRoom.finish(); // → FINISHED
        UUID roomId = gameRoom.getRoomId();

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));

        // When & Then
        assertThatThrownBy(() -> gameRoomService.joinRoom(roomId, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_FINISHED);
                });

        verify(gameParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("게임방 참가 실패 — 이미 ACTIVE 상태로 참가 중")
    void joinRoom_alreadyJoined() {
        // Given — ACTIVE 상태의 기존 참가자
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant activeParticipant = GameParticipant.createMember(gameRoom, OTHER_USER_ID);

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, OTHER_USER_ID))
                .willReturn(Optional.of(activeParticipant)); // ACTIVE 상태로 존재

        // When & Then
        assertThatThrownBy(() -> gameRoomService.joinRoom(roomId, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_ALREADY_JOINED);
                });

        verify(gameParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("게임방 참가 실패 — 인원 초과 (최대 6명)")
    void joinRoom_full() {
        // Given — 이력 없는 신규 유저, 이미 6명
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();

        given(gameRoomRepository.findByIdForUpdate(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, OTHER_USER_ID))
                .willReturn(Optional.empty()); // 이력 없음
        given(gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE))
                .willReturn(6); // 꽉 참

        // When & Then
        assertThatThrownBy(() -> gameRoomService.joinRoom(roomId, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_FULL);
                });

        verify(gameParticipantRepository, never()).save(any());
    }

    // === 헬퍼 메서드 ===

    private CreateRoomRequest createRequest() {
        return new CreateRoomRequest(10000000L, 6, 7);
    }

    private GameRoom createGameRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, 10000000L,
                6, 7, LocalDate.of(2020, 1, 2), LocalDate.of(2020, 7, 2));
    }
}
