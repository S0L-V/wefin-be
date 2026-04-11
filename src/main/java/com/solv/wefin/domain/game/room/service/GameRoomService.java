package com.solv.wefin.domain.game.room.service;

import java.math.BigDecimal;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.room.dto.CreateRoomCommand;
import com.solv.wefin.domain.game.room.dto.RoomDetailInfo;
import com.solv.wefin.domain.game.room.dto.RoomListInfo;
import com.solv.wefin.domain.game.room.dto.StartRoomInfo;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.event.GameRoomEvent;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final StockDailyRepository stockDailyRepository;
    private final ApplicationEventPublisher eventPublisher;


    //게임방 생성
    @Transactional //트랜잭션으로 방생성과 방장유저 지정 동시에 이루어짐
    public GameRoom createRoom(UUID userId, Long groupId, CreateRoomCommand command) {
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

        if (gameRoomRepository.existsByUserIdAndStartedAtBetween(userId, todayStart, todayEnd)) {
            throw new BusinessException(ErrorCode.ROOM_HOST_DAILY_LIMIT);
        }

        // start_date 랜덤 추출 + 거래일 보정
        LocalDate rangeStart = LocalDate.of(2021, 1, 1);
        LocalDate rangeEnd = LocalDate.of(2024, 12, 31).minusMonths(command.periodMonths());
        long daysBetween = ChronoUnit.DAYS.between(rangeStart, rangeEnd);
        long randomDays = ThreadLocalRandom.current().nextLong(daysBetween + 1);
        LocalDate rawStartDate = rangeStart.plusDays(randomDays);

        LocalDate startDate = stockDailyRepository.findLatestTradeDateOnOrBefore(rawStartDate)
                .orElseThrow(() -> new IllegalStateException(
                        "거래일 데이터가 없습니다. rawStartDate=" + rawStartDate));
        LocalDate endDate = startDate.plusMonths(command.periodMonths());

        //게임룸 저장
        GameRoom gameRoom = GameRoom.create(groupId, userId, command.seedMoney(), command.periodMonths(),
                command.moveDays(), startDate, endDate);
        gameRoomRepository.save(gameRoom);

        //첫 번째 참가자 = 방장
        GameParticipant host = GameParticipant.createLeader(gameRoom, userId);

        gameParticipantRepository.save(host);

        return gameRoom;
    }

    // 게임방 목록 조회
    public List<RoomListInfo> getRooms(Long groupId, UUID userId) {
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
                    return new RoomListInfo(room, playerCount);
                })
                .collect(Collectors.toList());
    }

    //방 상세 정보
    public RoomDetailInfo getRoomDetail(UUID roomId) {

        // 방 조회
        GameRoom gameRoom = gameRoomRepository.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        //참가자 상세
        List<GameParticipant> participants = gameParticipantRepository.findByGameRoomOrderByJoinedAtAsc(gameRoom);

        //참가자 상세 + 방 상세 저보
        return new RoomDetailInfo(gameRoom, participants);
    }

    /**
     * 게임 입장
     * 비관적 락으로 동시 입장 동시성 제어
     *
     */
    @Transactional
    public GameParticipant joinRoom(UUID roomId, UUID userId) {

        // 방 조회 + 락 시작
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 종료된 방인지 확인
        if (gameRoom.getStatus() == RoomStatus.FINISHED) {
            throw new BusinessException(ErrorCode.ROOM_FINISHED);
        }

        // 이미 참가한 유저인지 확인
        Optional<GameParticipant> existing = gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId);
        if (existing.isPresent()) {
            GameParticipant participant = existing.get();

            if (participant.getStatus() == ParticipantStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.ROOM_ALREADY_JOINED);
            }
            int currentPlayers = gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);
            if (currentPlayers >= 6) {
                throw new BusinessException(ErrorCode.ROOM_FULL);
            }
            participant.rejoin();
            eventPublisher.publishEvent(new GameRoomEvent(roomId, GameRoomEvent.EventType.PARTICIPANT_JOINED));
            return participant;
        }


        // 인원 초과 검사 (신규만, 근데 지금 최대 6명)
        int currentPlayers = gameParticipantRepository.countByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);
        if (currentPlayers >= 6) {
            throw new BusinessException(ErrorCode.ROOM_FULL);
        }

        // 5. 참가자 저장
        GameParticipant member = GameParticipant.createMember(gameRoom, userId);
        gameParticipantRepository.save(member);

        // 게임 진행 중 신규 참가자 시드머니 지급
        if (gameRoom.getStatus() == RoomStatus.IN_PROGRESS) {
            member.assignSeed(gameRoom.getSeed());
        }

        eventPublisher.publishEvent(new GameRoomEvent(roomId, GameRoomEvent.EventType.PARTICIPANT_JOINED));
        return member;
    }

    /**
     * 게임방 퇴장
     * 동시 퇴장 시 방장 위임 충돌 미리 방지 - 비관적 락
     */
    @Transactional
    public void leaveRoom(UUID roomId, UUID userId) {

        //게임방 조회
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        //finished면 error 처리
        if (gameRoom.getStatus() == RoomStatus.FINISHED) {
            throw new BusinessException(ErrorCode.ROOM_FINISHED);
        }
        //참가자 조회
        GameParticipant participant = gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 방장 여부 기록
        Boolean wasLeader = participant.getIsLeader();
        // 방장이면 비활성화
        if (wasLeader) {
            participant.resignLeader();
        }

        participant.leave();

        //남은 ACTIVE 유저 조회
        List<GameParticipant> remainingActive = gameParticipantRepository.findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);
        // 아무도 없으면 방 종료
        if (remainingActive.isEmpty()) {
            gameRoom.finish();
            return;
        }
        // 방장 위임
        if (wasLeader) {
            int randomIndex = ThreadLocalRandom.current().nextInt(remainingActive.size());
            GameParticipant newLeader = remainingActive.get(randomIndex);
            newLeader.assignLeader();
        }
        eventPublisher.publishEvent(new GameRoomEvent(roomId, GameRoomEvent.EventType.PARTICIPANT_LEFT));

    }

    //게임 시작
    @Transactional
    public StartRoomInfo startRoom(UUID roomId, UUID userId) {

        //검증 로직 Fail-Fast 패턴 적용
        // 1. 방 조회 + 비관적 락 (중복 시작 방지)
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. WAITING 상태에서만 시작 가능
        if (gameRoom.getStatus() != RoomStatus.WAITING) {
            throw new BusinessException(ErrorCode.ROOM_NOT_WAITING);
        }

        // 3. 요청자가 방장인지 확인
        GameParticipant host = gameParticipantRepository
                .findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        if (!host.getIsLeader()) {
            throw new BusinessException(ErrorCode.ROOM_NOT_HOST);
        }

        // 4. ACTIVE 참가자 2명 이상인지 확인
        List<GameParticipant> activeParticipants = gameParticipantRepository
                .findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);

        if (activeParticipants.size() < 2) {
            throw new BusinessException(ErrorCode.ROOM_MIN_PLAYERS);
        }

        // 5. 참가자 전원에게 시드머니 지급
        BigDecimal seedMoney = gameRoom.getSeed();
        for (GameParticipant participant : activeParticipants) {
            participant.assignSeed(seedMoney);
        }

        // 6. 첫 턴 생성
        GameTurn firstTurn = GameTurn.createFirst(gameRoom);
        gameTurnRepository.save(firstTurn);

        // 7. 방 상태 변경 (WAITING → IN_PROGRESS, startedAt 기록)
        gameRoom.start();

        eventPublisher.publishEvent(new GameRoomEvent(roomId, GameRoomEvent.EventType.GAME_STARTED));
        return new StartRoomInfo(gameRoom, firstTurn);
    }
}

/** creatRooom

 방장 1일 1회 제한 검사
 created_at  user_id로 조회
 종료날짜 계산
 started_at , periodMonths
 gameroom.builder() 후 save() < db저장


*/
/**
  1.participant로 처음 create 방 생성한 사람 = 방장
 방 생성과 방장 지정 동시에 이루어지게 트랜잭션
gameParticipant.builder()

 return 응답dto = createRoomResponse

 2. jpa 코드로 생성된 방 있으면 추가 생성 + 방장 방 동시 생성 차단
 유니크로 2차 차단그 아

 3. 방 목록 조회 그룹아이디 + status
 progress , wating / finished

 4. 과거 게임 이력 = finished + 유저아이디 ( 내 Id)

 5. 방정보 + 참가자 목록
 RoomDetailResponse
 참가자 정보 -> participantDetailDto로 변환


 6. 게임 입장
 조회 -> 방 상태 체크 -> 참가 여부 체크 -> 인원체 -> 입장

 재참가로 로직 변경
 조회 - 방 상태 체크 - 참가 기록 확인 (기록 있으면 인워 수 체크 후 재참가 로직 )- 인원체크 입장


 방 나가기

 동시 퇴장 방장 위임 충돌 방지 비관적 락으로 실행
 종료된 방인지 확인
 종료된 방 아니면
 참가자 -> left
 방장 -> 방장 위임
    남은 ACTIVE 찾아서 없으면 게임 종료
    있으면 랜덤 ACTIVE 유저 방장 위임 isLeader




 */


