package com.solv.wefin.web.game.room;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.room.dto.CreateRoomCommand;
import com.solv.wefin.domain.game.room.dto.RoomDetailInfo;
import com.solv.wefin.domain.game.room.dto.RoomListInfo;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.service.GameRoomService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;

    //로그인 구현 전 묵데이터
    private static final UUID TEMP_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final Long TEMP_GROUP_ID = 1L;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {

        CreateRoomCommand command = new CreateRoomCommand(
                request.getSeedMoney(), request.getPeriodMonths(), request.getMoveDays());
        GameRoom gameRoom = gameRoomService.createRoom(TEMP_USER_ID, TEMP_GROUP_ID, command);
        CreateRoomResponse response = CreateRoomResponse.from(gameRoom);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, response));

    }

    /**
     게임방 목록 조회
     get /api/rooms
     get /api/rooms?status=WAITING or in_progress or finished
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomListResponse>>> getRooms() {

        List<RoomListInfo> rooms = gameRoomService.getRooms(TEMP_GROUP_ID, TEMP_USER_ID);
        List<RoomListResponse> response = rooms.stream().map(
                r -> RoomListResponse.from(r.room(), r.playerCount())).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(@PathVariable UUID roomId){

        RoomDetailInfo detail = gameRoomService.getRoomDetail(roomId);

        List<ParticipantDetailDto> participantDtos = detail.participants().stream().map(
                p -> ParticipantDetailDto.from(p, "묵데이터유저")
        ).collect(Collectors.toList());

        RoomDetailResponse response = RoomDetailResponse.from(detail.room(), participantDtos);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 게임방 입장
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable UUID roomId) {

        GameParticipant participant = gameRoomService.joinRoom(roomId, TEMP_USER_ID);
        JoinRoomResponse response = JoinRoomResponse.from(participant);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

}

/**
 1. 방 생성 컨트롤러
 클라이언트에서 입력
 서비스 처리
 출력값 클라이언트 리턴

 그룹아이디, 유저아이디 묵데이터 입력 post요청
 createRoomRequest입력값 -> createRoom 서비스 출력값으로 응답 dto 클라이언트 리턴

 2. 게임방 목록 조회 컨트롤러
그룹 방 조회 -> 상태로 필터
조회 = get 요청
 서비스에서 그룹 id , status
 List roomListRespones

 -> status는 ㄴ클라이언트가 아닌 서버에서 처리 프론트에서는 화면에 맞게 필터링 해서 사용
 */
