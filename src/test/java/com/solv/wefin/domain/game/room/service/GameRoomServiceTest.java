package com.solv.wefin.domain.game.room.service;

import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.CreateRoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        // Given — 오늘 게임 시작 이력 없음
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
        // Given — 오늘 이미 게임 시작 이력 있음
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


    // 테스트용 Request 생성 헬퍼 메서드
    private CreateRoomRequest createRequest() {
        return new CreateRoomRequest(10000000L, 6, 7, "2020-01-02");
    }
}
