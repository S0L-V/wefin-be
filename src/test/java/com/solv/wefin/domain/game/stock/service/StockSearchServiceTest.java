package com.solv.wefin.domain.game.stock.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository.SectorKeywordCount;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockSearchServiceTest {

    @InjectMocks
    private StockSearchService stockSearchService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameTurnRepository gameTurnRepository;

    @Mock
    private GameParticipantRepository gameParticipantRepository;

    @Mock
    private StockDailyRepository stockDailyRepository;

    @Mock
    private StockInfoRepository stockInfoRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final Long TEST_GROUP_ID = 1L;
    private static final LocalDate TURN_DATE = LocalDate.of(2020, 1, 2);

    // === 종목 검색 테스트 ===

    @Test
    @DisplayName("종목 검색 성공 — 키워드에 매칭되는 종목 2개 반환")
    void searchStocks_success() {
        // Given — 방 존재, 참가자 확인, ACTIVE 턴 존재, 키워드 "삼성"에 2개 매칭
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        StockInfo samsung = StockInfo.create("005930", "삼성전자", "KOSPI", "전기전자");
        StockInfo samsungSDI = StockInfo.create("006400", "삼성SDI", "KOSPI", "전기전자");
        StockDaily daily1 = createStockDaily(samsung, TURN_DATE, new BigDecimal("71000"));
        StockDaily daily2 = createStockDaily(samsungSDI, TURN_DATE, new BigDecimal("450000"));

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockDailyRepository.searchByKeywordAndTradeDate(eq("%삼성%"), eq(TURN_DATE), any(Pageable.class)))
                .willReturn(List.of(daily1, daily2));

        // When
        List<StockDaily> result = stockSearchService.searchStocks(roomId, TEST_USER_ID, "삼성");

        // Then — 2개 반환, 각각 StockInfo 접근 가능
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockInfo().getStockName()).isEqualTo("삼성전자");
        assertThat(result.get(0).getOpenPrice()).isEqualTo(new BigDecimal("71000"));
        assertThat(result.get(1).getStockInfo().getStockName()).isEqualTo("삼성SDI");

        verify(stockDailyRepository).searchByKeywordAndTradeDate(eq("%삼성%"), eq(TURN_DATE), any(Pageable.class));
    }

    @Test
    @DisplayName("종목 검색 성공 — 결과 없으면 빈 리스트 반환")
    void searchStocks_emptyResult() {
        // Given — 방 존재, 참가자 확인, ACTIVE 턴 존재, 매칭 결과 없음
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockDailyRepository.searchByKeywordAndTradeDate(eq("%없는종목%"), eq(TURN_DATE), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        // When
        List<StockDaily> result = stockSearchService.searchStocks(roomId, TEST_USER_ID, "없는종목");

        // Then — 빈 리스트 (에러 아님)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("종목 검색 실패 — 존재하지 않는 방이면 ROOM_NOT_FOUND")
    void searchStocks_roomNotFound() {
        // Given — 존재하지 않는 roomId
        UUID fakeRoomId = UUID.fromString("00000000-0000-4000-a000-999999999999");
        given(gameRoomRepository.findById(fakeRoomId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockSearchService.searchStocks(fakeRoomId, TEST_USER_ID, "삼성"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
                });

        // 방을 못 찾았으니 턴 조회, 종목 검색은 호출되면 안 된다
        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any(), any());
    }

    @Test
    @DisplayName("종목 검색 실패 — ACTIVE 턴 없으면 GAME_NOT_STARTED")
    void searchStocks_gameNotStarted() {
        // Given — 방은 존재하지만 ACTIVE 턴이 없음 (게임 미시작)
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
        assertThatThrownBy(() -> stockSearchService.searchStocks(roomId, TEST_USER_ID, "삼성"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.GAME_NOT_STARTED);
                });

        // 턴이 없으니 종목 검색은 호출되면 안 된다
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any(), any());
    }

    @Test
    @DisplayName("종목 검색 실패 — 참가자가 아니면 ROOM_NOT_PARTICIPANT")
    void searchStocks_notParticipant() {
        // Given — 방은 존재하지만 해당 유저는 참가자가 아님
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        UUID outsiderUserId = UUID.fromString("00000000-0000-4000-a000-000000000099");

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, outsiderUserId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockSearchService.searchStocks(roomId, outsiderUserId, "삼성"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_PARTICIPANT);
                });

        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any(), any());
    }

    @Test
    @DisplayName("종목 검색 — 공백만 있는 keyword는 빈 리스트 반환, Repository 호출 안 함")
    void searchStocks_blankKeyword() {
        // Given — keyword가 공백 문자열
        UUID roomId = UUID.fromString("00000000-0000-4000-a000-000000000010");

        // When
        List<StockDaily> result = stockSearchService.searchStocks(roomId, TEST_USER_ID, "   ");

        // Then — 빈 리스트 반환, 그리고 그 어떤 Repository도 호출되지 않음
        // (방/참가자/턴 조회조차 없이 즉시 종료되어야 함)
        assertThat(result).isEmpty();
        verify(gameRoomRepository, never()).findById(any());
        verify(gameParticipantRepository, never()).findByGameRoomAndUserId(any(), any());
        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any(), any());
    }

    @Test
    @DisplayName("종목 검색 — null keyword도 빈 리스트 반환")
    void searchStocks_nullKeyword() {
        // Given — keyword가 null
        UUID roomId = UUID.fromString("00000000-0000-4000-a000-000000000010");

        // When
        List<StockDaily> result = stockSearchService.searchStocks(roomId, TEST_USER_ID, null);

        // Then
        assertThat(result).isEmpty();
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any(), any());
    }

    // === 섹터 목록 조회 테스트 ===

    @Test
    @DisplayName("섹터 목록 조회 성공 — 섹터별 키워드 개수 반환")
    void getSectors_success() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));

        SectorKeywordCount it = createSectorKeywordCount("IT", 5L);
        SectorKeywordCount finance = createSectorKeywordCount("금융", 6L);
        given(stockInfoRepository.findSectorsWithKeywordCount()).willReturn(List.of(it, finance));

        // When
        List<SectorKeywordCount> result = stockSearchService.getSectors(roomId, TEST_USER_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSector()).isEqualTo("IT");
        assertThat(result.get(0).getKeywordCount()).isEqualTo(5L);
        assertThat(result.get(1).getSector()).isEqualTo("금융");
    }

    @Test
    @DisplayName("섹터 목록 조회 실패 — 참가자가 아니면 ROOM_NOT_PARTICIPANT")
    void getSectors_notParticipant() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        UUID outsider = UUID.fromString("00000000-0000-4000-a000-000000000099");

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, outsider))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockSearchService.getSectors(roomId, outsider))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_NOT_PARTICIPANT));

        verify(stockInfoRepository, never()).findSectorsWithKeywordCount();
    }

    // === 키워드 목록 조회 테스트 ===

    @Test
    @DisplayName("키워드 목록 조회 성공 — 해당 섹터의 키워드 반환")
    void getKeywords_success() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(stockInfoRepository.findKeywordsBySector("IT"))
                .willReturn(List.of("반도체", "소프트웨어", "하드웨어"));

        // When
        List<String> result = stockSearchService.getKeywords(roomId, TEST_USER_ID, "IT");

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly("반도체", "소프트웨어", "하드웨어");
    }

    @Test
    @DisplayName("키워드 목록 조회 — 존재하지 않는 섹터면 빈 리스트 반환")
    void getKeywords_emptySector() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(stockInfoRepository.findKeywordsBySector("없는섹터"))
                .willReturn(Collections.emptyList());

        // When
        List<String> result = stockSearchService.getKeywords(roomId, TEST_USER_ID, "없는섹터");

        // Then
        assertThat(result).isEmpty();
    }

    // === 섹터+키워드 종목 목록 조회 테스트 ===

    @Test
    @DisplayName("종목 목록 조회 성공 — 종가 포함, 이름순 정렬")
    void getStocksByKeyword_success() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);

        StockInfo samsung = StockInfo.create("005930", "삼성전자", "KOSPI", "IT");
        StockInfo samsungSDI = StockInfo.create("006400", "삼성SDI", "KOSPI", "IT");
        StockDaily daily1 = createStockDaily(samsung, TURN_DATE, new BigDecimal("71000"));
        StockDaily daily2 = createStockDaily(samsungSDI, TURN_DATE, new BigDecimal("450000"));

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockInfoRepository.findBySectorAndKeywordOrderByStockNameAsc("IT", "반도체"))
                .willReturn(List.of(samsung, samsungSDI));
        given(stockDailyRepository.findAllByStockInfoInAndTradeDate(List.of(samsung, samsungSDI), TURN_DATE))
                .willReturn(List.of(daily1, daily2));

        // When
        List<StockDaily> result = stockSearchService.getStocksByKeyword(roomId, TEST_USER_ID, "IT", "반도체");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockInfo().getStockName()).isEqualTo("삼성전자");
        assertThat(result.get(1).getStockInfo().getStockName()).isEqualTo("삼성SDI");
    }

    @Test
    @DisplayName("종목 목록 조회 — 해당 키워드에 종목이 없으면 빈 리스트")
    void getStocksByKeyword_noStocks() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockInfoRepository.findBySectorAndKeywordOrderByStockNameAsc("없는섹터", "없는키워드"))
                .willReturn(Collections.emptyList());

        // When
        List<StockDaily> result = stockSearchService.getStocksByKeyword(roomId, TEST_USER_ID, "없는섹터", "없는키워드");

        // Then
        assertThat(result).isEmpty();
        verify(stockDailyRepository, never()).findAllByStockInfoInAndTradeDate(any(), any());
    }

    @Test
    @DisplayName("종목 목록 조회 — 턴 날짜에 종가 없는 종목은 제외")
    void getStocksByKeyword_excludeNoPriceStocks() {
        // Given — 종목 2개 중 1개만 해당 날짜에 종가 존재
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);

        StockInfo withPrice = StockInfo.create("005930", "삼성전자", "KOSPI", "IT");
        StockInfo noPrice = StockInfo.create("999999", "상장폐지종목", "KOSPI", "IT");
        StockDaily daily = createStockDaily(withPrice, TURN_DATE, new BigDecimal("71000"));

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockInfoRepository.findBySectorAndKeywordOrderByStockNameAsc("IT", "반도체"))
                .willReturn(List.of(withPrice, noPrice));
        given(stockDailyRepository.findAllByStockInfoInAndTradeDate(List.of(withPrice, noPrice), TURN_DATE))
                .willReturn(List.of(daily));

        // When
        List<StockDaily> result = stockSearchService.getStocksByKeyword(roomId, TEST_USER_ID, "IT", "반도체");

        // Then — 종가 있는 1개만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockInfo().getStockName()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("종목 목록 조회 실패 — ACTIVE 턴 없으면 GAME_NOT_STARTED")
    void getStocksByKeyword_gameNotStarted() {
        // Given
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);

        given(gameRoomRepository.findById(roomId)).willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockSearchService.getStocksByKeyword(roomId, TEST_USER_ID, "IT", "반도체"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GAME_NOT_STARTED));
    }

    // === 헬퍼 메서드 ===

    private GameRoom createGameRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, new BigDecimal("10000000"),
                6, 7, LocalDate.of(2020, 1, 2), LocalDate.of(2020, 7, 2));
    }

    private StockDaily createStockDaily(StockInfo stockInfo, LocalDate tradeDate, BigDecimal openPrice) {
        return StockDaily.create(stockInfo, tradeDate,
                openPrice,
                openPrice.add(new BigDecimal("1000")),
                openPrice.subtract(new BigDecimal("1000")),
                openPrice.add(new BigDecimal("500")),
                new BigDecimal("1000000"),
                new BigDecimal("1.5"));
    }

    private SectorKeywordCount createSectorKeywordCount(String sector, Long keywordCount) {
        SectorKeywordCount projection = mock(SectorKeywordCount.class);
        lenient().when(projection.getSector()).thenReturn(sector);
        lenient().when(projection.getKeywordCount()).thenReturn(keywordCount);
        return projection;
    }
}
