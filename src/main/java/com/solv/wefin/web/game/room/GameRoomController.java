package com.solv.wefin.web.game.room;

import com.solv.wefin.domain.game.room.service.GameRoomService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.CreateRoomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;

    //로그인 구현 전 묵데이터
    private static final UUID TEMP_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long TEMP_GROUP_ID = 1L;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {

        CreateRoomResponse response = gameRoomService.createRoom(TEMP_USER_ID, TEMP_GROUP_ID, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));

    }


}

/**
 클라이언트에서 입력
 서비스 처리
 출력값 클라이언트 리턴

 그룹아이디, 유저아이디 묵데이터 입력 post요청
 createRoomRequest입력값 -> createRoom 서비스 출력값으로 응답 dto 클라이언트 리턴
 */
