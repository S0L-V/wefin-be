package com.solv.wefin.web.game.room;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.result.dto.GameHistoryInfo;
import com.solv.wefin.domain.game.result.service.GameResultService;
import com.solv.wefin.domain.game.room.dto.CreateRoomCommand;
import com.solv.wefin.domain.game.room.dto.RoomDetailInfo;
import com.solv.wefin.domain.game.room.dto.RoomListInfo;
import com.solv.wefin.domain.game.room.dto.StartRoomInfo;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.room.service.GameRoomService;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.common.PageInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.game.room.dto.LeaveRoomResponse;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class
GameRoomController {

    private final GameRoomService gameRoomService;
    private final GameResultService gameResultService;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateRoomRequest request) {

        Long groupId = getActiveGroupId(userId);
        CreateRoomCommand command = new CreateRoomCommand(
                request.getSeedMoney(), request.getPeriodMonths(), request.getMoveDays());
        GameRoom gameRoom = gameRoomService.createRoom(userId, groupId, command);
        CreateRoomResponse response = CreateRoomResponse.from(gameRoom);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, response));

    }

    /**
     게임방 목록 조회
     get /api/rooms
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomListResponse>>> getRooms(@AuthenticationPrincipal UUID userId) {
        Long groupId = getActiveGroupId(userId);
        List<RoomListInfo> rooms = gameRoomService.getRooms(groupId, userId);
        List<RoomListResponse> response = rooms.stream().map(
                r -> RoomListResponse.from(r.room(), r.playerCount())).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 과거 게임 이력 페이징 조회.
     * 내가 FINISHED한 게임의 결과(수익률/순위/자산)를 최신순으로 반환.
     * 정렬은 createdAt DESC 고정 — 클라이언트가 정렬을 조작하지 못하게 서버에서 강제.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<GameHistoryPageResponse>> getHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long groupId = getActiveGroupId(userId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<GameHistoryInfo> historyPage = gameResultService.getMyGameHistory(groupId, userId, pageable);

        List<GameHistoryResponse> content = historyPage.getContent().stream()
                .map(GameHistoryResponse::from)
                .toList();

        GameHistoryPageResponse response = new GameHistoryPageResponse(content, PageInfo.from(historyPage));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    record GameHistoryPageResponse(List<GameHistoryResponse> content, PageInfo pageInfo) {}

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(@PathVariable UUID roomId){

        RoomDetailInfo detail = gameRoomService.getRoomDetail(roomId);

        List<UUID> userIds = detail.participants().stream()
                .map(GameParticipant::getUserId).toList();
        Map<UUID, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getNickname));

        List<ParticipantDetailDto> participantDtos = detail.participants().stream().map(
                p -> ParticipantDetailDto.from(p, nicknameMap.getOrDefault(p.getUserId(), "알 수 없음"))
        ).collect(Collectors.toList());

        RoomDetailResponse response = RoomDetailResponse.from(detail.room(), participantDtos);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 게임방 입장
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable UUID roomId, @AuthenticationPrincipal UUID userId) {

        GameParticipant participant = gameRoomService.joinRoom(roomId, userId);
        JoinRoomResponse response = JoinRoomResponse.from(participant);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    //게임방 퇴장
    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<LeaveRoomResponse>> leaveRoom(@PathVariable UUID roomId, @AuthenticationPrincipal UUID userId) {

        gameRoomService.leaveRoom(roomId, userId);
        LeaveRoomResponse response = LeaveRoomResponse.success();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    //게임 시작
    @PostMapping("/{roomId}/start")
    public ResponseEntity<ApiResponse<StartRoomResponse>> startRoom(
            @PathVariable UUID roomId, @AuthenticationPrincipal UUID userId) {

        StartRoomInfo info = gameRoomService.startRoom(roomId, userId);
        StartRoomResponse response = StartRoomResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getActiveGroupId(UUID userId) {
        return groupMemberRepository
                .findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND))
                .getGroup().getId();
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
