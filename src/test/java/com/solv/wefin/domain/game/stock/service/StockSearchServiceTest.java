package com.solv.wefin.domain.game.stock.service;

import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
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
        given(stockDailyRepository.searchByKeywordAndTradeDate("%삼성%", TURN_DATE))
                .willReturn(List.of(daily1, daily2));

        // When
        List<StockDaily> result = stockSearchService.searchStocks(roomId, TEST_USER_ID, "삼성");

        // Then — 2개 반환, 각각 StockInfo 접근 가능
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockInfo().getStockName()).isEqualTo("삼성전자");
        assertThat(result.get(0).getOpenPrice()).isEqualTo(new BigDecimal("71000"));
        assertThat(result.get(1).getStockInfo().getStockName()).isEqualTo("삼성SDI");

        verify(stockDailyRepository).searchByKeywordAndTradeDate("%삼성%", TURN_DATE);
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
        given(stockDailyRepository.searchByKeywordAndTradeDate("%없는종목%", TURN_DATE))
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
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any());
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
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any());
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
        verify(stockDailyRepository, never()).searchByKeywordAndTradeDate(any(), any());
    }

    // === 헬퍼 메서드 ===

    private GameRoom createGameRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, 10000000L,
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
}
