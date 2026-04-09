package com.solv.wefin.domain.game.stock.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockChartServiceTest {

    @InjectMocks
    private StockChartService stockChartService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameTurnRepository gameTurnRepository;

    @Mock
    private GameParticipantRepository gameParticipantRepository;

    @Mock
    private StockInfoRepository stockInfoRepository;

    @Mock
    private StockDailyRepository stockDailyRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final Long TEST_GROUP_ID = 1L;
    private static final String SYMBOL = "005930";
    private static final LocalDate TURN_DATE = LocalDate.of(2022, 1, 3);
    private static final LocalDate TWO_YEARS_AGO = TURN_DATE.minusYears(2);

    // === 차트 조회 테스트 ===

    @Test
    @DisplayName("차트 조회 성공 — 2년치 일봉 데이터 반환")
    void getChart_success() {
        // Given — 방 존재, 참가자 확인, ACTIVE 턴 존재, 종목 존재, 일봉 3건
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        StockInfo stockInfo = StockInfo.create(SYMBOL, "삼성전자", "KOSPI", "전기전자");

        StockDaily daily1 = createStockDaily(stockInfo, LocalDate.of(2020, 1, 3), new BigDecimal("55000"));
        StockDaily daily2 = createStockDaily(stockInfo, LocalDate.of(2020, 6, 15), new BigDecimal("60000"));
        StockDaily daily3 = createStockDaily(stockInfo, TURN_DATE, new BigDecimal("71000"));

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockInfoRepository.findById(SYMBOL))
                .willReturn(Optional.of(stockInfo));
        given(stockDailyRepository.findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
                stockInfo, TWO_YEARS_AGO, TURN_DATE))
                .willReturn(List.of(daily1, daily2, daily3));

        // When
        List<StockDaily> result = stockChartService.getChart(SYMBOL, roomId, TEST_USER_ID);

        // Then — 3건 반환, 날짜 오름차순
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTradeDate()).isEqualTo(LocalDate.of(2020, 1, 3));
        assertThat(result.get(2).getTradeDate()).isEqualTo(TURN_DATE);
        assertThat(result.get(2).getOpenPrice()).isEqualTo(new BigDecimal("71000"));

        verify(stockDailyRepository).findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
                stockInfo, TWO_YEARS_AGO, TURN_DATE);
    }

    @Test
    @DisplayName("차트 조회 성공 — 데이터 없으면 빈 리스트 반환")
    void getChart_emptyResult() {
        // Given — 방, 참가자, 턴, 종목 존재하지만 일봉 데이터 없음
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        GameTurn activeTurn = GameTurn.createFirst(gameRoom);
        GameParticipant participant = GameParticipant.createLeader(gameRoom, TEST_USER_ID);
        StockInfo stockInfo = StockInfo.create(SYMBOL, "삼성전자", "KOSPI", "전기전자");

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE))
                .willReturn(Optional.of(activeTurn));
        given(stockInfoRepository.findById(SYMBOL))
                .willReturn(Optional.of(stockInfo));
        given(stockDailyRepository.findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
                stockInfo, TWO_YEARS_AGO, TURN_DATE))
                .willReturn(Collections.emptyList());

        // When
        List<StockDaily> result = stockChartService.getChart(SYMBOL, roomId, TEST_USER_ID);

        // Then — 빈 리스트 (에러 아님)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("차트 조회 실패 — 존재하지 않는 방이면 ROOM_NOT_FOUND")
    void getChart_roomNotFound() {
        // Given
        UUID fakeRoomId = UUID.fromString("00000000-0000-4000-a000-999999999999");
        given(gameRoomRepository.findById(fakeRoomId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockChartService.getChart(SYMBOL, fakeRoomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
                });

        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(stockInfoRepository, never()).findById(any());
        verify(stockDailyRepository, never())
                .findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("차트 조회 실패 — ACTIVE 턴 없으면 GAME_NOT_STARTED")
    void getChart_gameNotStarted() {
        // Given
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
        assertThatThrownBy(() -> stockChartService.getChart(SYMBOL, roomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.GAME_NOT_STARTED);
                });

        verify(stockInfoRepository, never()).findById(any());
        verify(stockDailyRepository, never())
                .findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("차트 조회 실패 — 존재하지 않는 종목이면 GAME_STOCK_NOT_FOUND")
    void getChart_stockNotFound() {
        // Given — 방, 참가자, 턴은 존재하지만 종목 없음
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
        given(stockInfoRepository.findById("999999"))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockChartService.getChart("999999", roomId, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.GAME_STOCK_NOT_FOUND);
                });

        verify(stockDailyRepository, never())
                .findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("차트 조회 실패 — 참가자가 아니면 ROOM_NOT_PARTICIPANT")
    void getChart_notParticipant() {
        // Given — 방은 존재하지만 해당 유저는 참가자가 아님
        GameRoom gameRoom = createGameRoom();
        UUID roomId = gameRoom.getRoomId();
        UUID outsiderUserId = UUID.fromString("00000000-0000-4000-a000-000000000099");

        given(gameRoomRepository.findById(roomId))
                .willReturn(Optional.of(gameRoom));
        given(gameParticipantRepository.findByGameRoomAndUserId(gameRoom, outsiderUserId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stockChartService.getChart(SYMBOL, roomId, outsiderUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_PARTICIPANT);
                });

        verify(gameTurnRepository, never()).findByGameRoomAndStatus(any(), any());
        verify(stockInfoRepository, never()).findById(any());
        verify(stockDailyRepository, never())
                .findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    // === 헬퍼 메서드 ===

    private GameRoom createGameRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, 10000000L,
                6, 7, LocalDate.of(2022, 1, 3), LocalDate.of(2022, 7, 3));
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
}
