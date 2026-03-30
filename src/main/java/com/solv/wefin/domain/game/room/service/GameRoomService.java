package com.solv.wefin.domain.game.room.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.game.room.dto.request.CreateRoomRequest;
import com.solv.wefin.web.game.room.dto.response.CreateRoomResponse;
import com.solv.wefin.web.game.room.dto.response.ParticipantDetailDto;
import com.solv.wefin.web.game.room.dto.response.RoomDetailResponse;
import com.solv.wefin.web.game.room.dto.response.RoomListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;

    //게임방 생성
    @Transactional //트랜잭션으로 방생성과 방장유저 지정 동시에 이루어짐
    public CreateRoomResponse createRoom(UUID userId, Long groupId, CreateRoomRequest request) {
        //그룹 게임방 있으면 차단
        List<RoomStatus> activeStatuses = List.of(RoomStatus.WAITING, RoomStatus.IN_PROGRESS);
        if (gameRoomRepository.existsByGroupIdAndStatusIn(groupId, activeStatuses)) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_EXISTS);
        }
        // 방장 방 동시 생성 방지
        if (gameRoomRepository.existsByUserIdAndStatusIn(userId, activeStatuses)) {
            throw new BusinessException(ErrorCode.ROOM_HOST_ALREADY_EXISTS);
        }

            // 방장 횟수 제한 1일 1회
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        if(gameRoomRepository.existsByUserIdAndStartedAtBetween(userId, todayStart, todayEnd)) {
            throw new BusinessException(ErrorCode.ROOM_HOST_DAILY_LIMIT);
        }

        //endDate 계산
        LocalDate rangeStart = LocalDate.of(2020,1,1);
        LocalDate rangeEnd = LocalDate.of(2024, 12, 31).minusMonths(request.getPeriodMonths());
        long daysBetween = ChronoUnit.DAYS.between(rangeStart, rangeEnd);
        long randomDays = ThreadLocalRandom.current().nextLong(daysBetween + 1);
        LocalDate startDate = rangeStart.plusDays(randomDays);
        LocalDate endDate = startDate.plusMonths(request.getPeriodMonths());

        //게임룸 저장
        GameRoom gameRoom = GameRoom.create(groupId, userId, request.getSeedMoney(), request.getPeriodMonths(),
                request.getMoveDays(), startDate, endDate);
        gameRoomRepository.save(gameRoom);

        //첫 번째 참가자 = 방장
        GameParticipant host = GameParticipant.createLeader(gameRoom, userId);

        gameParticipantRepository.save(host);

        return CreateRoomResponse.from(gameRoom);
    }

    public List<RoomListResponse> getRooms(Long groupId, UUID userId) {
        //그룹 활성화된 방
        List<RoomStatus> activeStatuses = List.of(RoomStatus.WAITING, RoomStatus.IN_PROGRESS);
        List<GameRoom> activeRooms = gameRoomRepository.findByGroupIdAndStatusIn(groupId, activeStatuses);

        // 내 과거 기록
        List<GameRoom> myFinishedRooms = gameRoomRepository.findFinishedRoomsByGroupIdAndUserId((groupId), userId);

        List<GameRoom> rooms = new ArrayList<>();
        rooms.addAll(activeRooms);
        rooms.addAll(myFinishedRooms);

        //참가자 수 count
        return rooms.stream().map(room -> {
                    int playerCount = gameParticipantRepository.countByGameRoomAndStatus(room, ParticipantStatus.ACTIVE);
                    return RoomListResponse.from(room, playerCount);
                })
                .collect(Collectors.toList());
    }

    //방 상세 정보
    public RoomDetailResponse getRoomDetail(UUID roomId) {

        // 방 조회
        GameRoom gameRoom= gameRoomRepository.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        //참가자 상세
        List<ParticipantDetailDto> participants = gameParticipantRepository
                .findByGameRoomOrderByJoinedAtAsc(gameRoom)
                .stream()
                .map(p->ParticipantDetailDto.from(p,"유저묵데이터"))
                .collect(Collectors.toList());
        //참가자 상세 + 방 상세 저보
        return RoomDetailResponse.from(gameRoom, participants);
    }


}


/** creatRooom

 방장 1일 1회 제한 검사
 created_at  user_id로 조회
 종료날짜 계산
 started_at , periodMonths
 gameroom.builder() 후 save() < db저장


*/
/**participant로 처음 create 방 생성한 사람 = 방장
 방 생성과 방장 지정 동시에 이루어지게 트랜잭션
gameParticipant.builder()

 return 응답dto = createRoomResponse

 jpa 코드로 생성된 방 있으면 추가 생성 + 방장 방 동시 생성 차단
 유니크로 2차 차단그 아

 방 목록 조회 그룹아이디 + status
 progress , wating / finished

 과거 게임 이력 = finished + 유저아이디 ( 내 Id)

 방정보 + 참가자 목록
 RoomDetailResponse
 참가자 정보 -> participantDetailDto로 변환

 */
/**cancel room
 *
 */


