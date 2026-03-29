package com.solv.wefin.domain.game.room.service;

import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.CreateRoomResponse;
import com.solv.wefin.web.game.room.dto.response.RoomListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
                any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(false);

        CreateRoomRequest request = createRequest();

        // When — 방 생성 호출
        CreateRoomResponse response = gameRoomService.createRoom(TEST_USER_ID, TEST_GROUP_ID, request);

        // Then — 결과 검증
        assertThat(response.getStatus()).isEqualTo("WAITING");

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
                any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
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

    // ===  게임방 목록 조회 테스트 ===

    @Test
    @DisplayName("게임방 목록 조회 — status 없으면 그룹 전체 방 조회")
    void getRooms_withoutStatus() {
        // Given — 그룹에 방 2개 존재
        GameRoom room1 = createGameRoom();
        GameRoom room2 = createGameRoom();
        given(gameRoomRepository.findByGroupId(TEST_GROUP_ID))
                .willReturn(List.of(room1, room2));
        given(gameParticipantRepository.countByGameRoomAndStatus(any(GameRoom.class), eq("ACTIVE")))
                .willReturn(1);

        // When — status 없이 조회
        List<RoomListResponse> result = gameRoomService.getRooms(TEST_GROUP_ID, null);

        // Then — 2개 반환
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("WAITING");
        assertThat(result.get(0).getCurrentPlayers()).isEqualTo(1);

        // findByGroupId가 호출됐는지 확인
        verify(gameRoomRepository).findByGroupId(TEST_GROUP_ID);
    }

    @Test
    @DisplayName("게임방 목록 조회 — status 있으면 해당 상태만 필터링")
    void getRooms_withStatus() {
        // Given — WAITING 방 1개
        GameRoom room = createGameRoom();
        given(gameRoomRepository.findByGroupIdAndStatus(TEST_GROUP_ID, "WAITING"))
                .willReturn(List.of(room));
        given(gameParticipantRepository.countByGameRoomAndStatus(any(GameRoom.class), eq("ACTIVE")))
                .willReturn(3);

        // When — status=WAITING으로 조회
        List<RoomListResponse> result = gameRoomService.getRooms(TEST_GROUP_ID, "WAITING");

        // Then — 1개 반환, 참가자 3명
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentPlayers()).isEqualTo(3);

        // findByGroupIdAndStatus가 호출됐는지 확인
        verify(gameRoomRepository).findByGroupIdAndStatus(TEST_GROUP_ID, "WAITING");
    }

    @Test
    @DisplayName("게임방 목록 조회 — 결과 없으면 빈 리스트 반환")
    void getRooms_empty() {
        // Given — 그룹에 방 없음
        given(gameRoomRepository.findByGroupId(TEST_GROUP_ID))
                .willReturn(Collections.emptyList());

        // When
        List<RoomListResponse> result = gameRoomService.getRooms(TEST_GROUP_ID, null);

        // Then — 빈 리스트 (에러 아님)
        assertThat(result).isEmpty();
    }

    // === 헬퍼 메서드 ===

    private CreateRoomRequest createRequest() {
        return new CreateRoomRequest(10000000L, 6, 7);
    }

    private GameRoom createGameRoom() {
        return GameRoom.builder()
                .groupId(TEST_GROUP_ID)
                .userId(TEST_USER_ID)
                .seed(10000000L)
                .periodMonth(6)
                .moveDays(7)
                .startDate(LocalDate.of(2020, 1, 2))
                .endDate(LocalDate.of(2020, 7, 2))
                .build();
    }
}
