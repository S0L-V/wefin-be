package com.solv.wefin.domain.game.order.service;

import com.solv.wefin.domain.game.holding.entity.GameHolding;
import com.solv.wefin.domain.game.holding.repository.GameHoldingRepository;
import com.solv.wefin.domain.game.order.dto.OrderCommand;
import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.order.entity.OrderType;
import com.solv.wefin.domain.game.order.repository.GameOrderRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameOrderServiceTest {

    @InjectMocks
    private GameOrderService orderService;

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
    @Mock
    private GameOrderRepository gameOrderRepository;
    @Mock
    private GameHoldingRepository gameHoldingRepository;

    private static final UUID TEST_ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final Long TEST_GROUP_ID = 1L;
    private static final String TEST_SYMBOL = "005930";
    private static final LocalDate TEST_TRADE_DATE = LocalDate.of(2022, 3, 2);

    // === 매수 테스트 ===

    @Nested
    @DisplayName("매수 (BUY)")
    class BuyTests {

        @Test
        @DisplayName("매수 성공 — 신규 종목, 주문 저장 + 보유종목 생성 + 잔고 차감")
        void buy_success_newHolding() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("55500"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.empty());
            given(gameOrderRepository.save(any(GameOrder.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            GameOrder result = orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10));

            // Then
            // 주문 검증
            assertThat(result.getOrderType()).isEqualTo(OrderType.BUY);
            assertThat(result.getQuantity()).isEqualTo(10);
            assertThat(result.getOrderPrice()).isEqualByComparingTo(new BigDecimal("55500"));
            assertThat(result.getTax()).isEqualByComparingTo(BigDecimal.ZERO);

            // 수수료: 55500 × 10 × 0.00015 = 83.25
            BigDecimal expectedFee = new BigDecimal("55500").multiply(BigDecimal.TEN)
                    .multiply(new BigDecimal("0.00015")).setScale(2, RoundingMode.HALF_UP);
            assertThat(result.getFee()).isEqualByComparingTo(expectedFee);

            // 잔고 차감: 555000 + 83.25 = 555083.25
            BigDecimal expectedCash = new BigDecimal("10000000")
                    .subtract(new BigDecimal("555000").add(expectedFee));
            assertThat(participant.getSeed()).isEqualByComparingTo(expectedCash);

            // 보유종목 신규 생성 확인
            verify(gameHoldingRepository).save(any(GameHolding.class));
        }

        @Test
        @DisplayName("매수 성공 — 기존 보유 종목, 가중평균 재계산")
        void buy_success_existingHolding() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("72000"));

            // 기존 보유: 10주 @ 70000
            GameHolding existing = GameHolding.create(participant, stockInfo, 10, new BigDecimal("70000"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.of(existing));
            given(gameOrderRepository.save(any(GameOrder.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When — 5주 추가 매수 @ 72000
            orderService.placeOrder(TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 5));

            // Then — 가중평균: (10×70000 + 5×72000) / 15 = 70666.67
            assertThat(existing.getQuantity()).isEqualTo(15);
            BigDecimal expectedAvg = new BigDecimal("70000").multiply(BigDecimal.TEN)
                    .add(new BigDecimal("72000").multiply(BigDecimal.valueOf(5)))
                    .divide(BigDecimal.valueOf(15), 2, RoundingMode.HALF_UP);
            assertThat(existing.getAvgPrice()).isEqualByComparingTo(expectedAvg);

            // 신규 save 호출 없음 (기존 엔티티 dirty checking)
            verify(gameHoldingRepository, never()).save(any(GameHolding.class));
        }

        @Test
        @DisplayName("매수 실패 — 잔고 부족")
        void buy_fail_insufficientBalance() {
            // Given — 잔고 1000원, 55500원짜리 10주 매수 시도
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("1000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("55500"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_INSUFFICIENT_BALANCE);

            verify(gameOrderRepository, never()).save(any());
        }
    }

    // === 매도 테스트 ===

    @Nested
    @DisplayName("매도 (SELL)")
    class SellTests {

        @Test
        @DisplayName("매도 성공 — 일부 매도, 잔고 증가 + 수량 감소")
        void sell_success_partial() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("5000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("60000"));

            // 보유: 20주
            GameHolding holding = GameHolding.create(participant, stockInfo, 20, new BigDecimal("55000"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.of(holding));
            given(gameOrderRepository.save(any(GameOrder.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When — 10주 매도 @ 60000
            GameOrder result = orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.SELL, 10));

            // Then
            assertThat(result.getOrderType()).isEqualTo(OrderType.SELL);
            assertThat(result.getQuantity()).isEqualTo(10);

            // 수수료: 600000 × 0.00015 = 90.00
            BigDecimal expectedFee = new BigDecimal("600000")
                    .multiply(new BigDecimal("0.00015")).setScale(2, RoundingMode.HALF_UP);
            // 세금: 600000 × 0.0018 = 1080.00
            BigDecimal expectedTax = new BigDecimal("600000")
                    .multiply(new BigDecimal("0.0018")).setScale(2, RoundingMode.HALF_UP);
            assertThat(result.getFee()).isEqualByComparingTo(expectedFee);
            assertThat(result.getTax()).isEqualByComparingTo(expectedTax);

            // 잔고: 5000000 + (600000 - 90 - 1080) = 5598830
            BigDecimal netProceeds = new BigDecimal("600000").subtract(expectedFee).subtract(expectedTax);
            BigDecimal expectedCash = new BigDecimal("5000000").add(netProceeds);
            assertThat(participant.getSeed()).isEqualByComparingTo(expectedCash);

            // 보유 수량 감소 (삭제 아님)
            assertThat(holding.getQuantity()).isEqualTo(10);
            verify(gameHoldingRepository, never()).delete(any());
        }

        @Test
        @DisplayName("매도 성공 — 전량 매도, 보유종목 삭제")
        void sell_success_allQuantity_holdingDeleted() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("5000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("60000"));

            // 보유: 10주
            GameHolding holding = GameHolding.create(participant, stockInfo, 10, new BigDecimal("55000"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.of(holding));
            given(gameOrderRepository.save(any(GameOrder.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When — 전량 10주 매도
            orderService.placeOrder(TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.SELL, 10));

            // Then — 전량 매도 → 바로 삭제 (reduceQuantity 호출 없이)
            verify(gameHoldingRepository).delete(holding);
        }

        @Test
        @DisplayName("매도 실패 — 보유 종목 없음")
        void sell_fail_notHeld() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("55500"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.SELL, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_STOCK_NOT_HELD);
        }

        @Test
        @DisplayName("매도 실패 — 보유 수량 부족")
        void sell_fail_insufficientHoldings() {
            // Given — 5주 보유, 10주 매도 시도
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));
            StockInfo stockInfo = createStockInfo();
            StockDaily stockDaily = createStockDaily(stockInfo, new BigDecimal("55500"));

            GameHolding holding = GameHolding.create(participant, stockInfo, 5, new BigDecimal("55000"));

            setupCommonMocks(room, turn, participant, stockInfo, stockDaily);
            given(gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo))
                    .willReturn(Optional.of(holding));

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.SELL, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_INSUFFICIENT_HOLDINGS);
        }
    }

    // === 공통 검증 테스트 ===

    @Nested
    @DisplayName("공통 검증")
    class ValidationTests {

        @Test
        @DisplayName("실패 — 방이 존재하지 않음")
        void fail_roomNotFound() {
            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 — 게임 미시작 (WAITING 상태)")
        void fail_gameNotStarted() {
            GameRoom room = createWaitingRoom();
            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }

        @Test
        @DisplayName("실패 — 종목을 찾을 수 없음")
        void fail_stockNotFound() {
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));

            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                    .willReturn(Optional.of(turn));
            given(gameParticipantRepository.findByGameRoomAndUserIdForUpdate(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));
            given(stockInfoRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_STOCK_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 — 해당 날짜 주가 데이터 없음")
        void fail_stockPriceNotFound() {
            GameRoom room = createGameRoom();
            GameTurn turn = createGameTurn(room);
            GameParticipant participant = createParticipant(room, new BigDecimal("10000000"));
            StockInfo stockInfo = createStockInfo();

            given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                    .willReturn(Optional.of(turn));
            given(gameParticipantRepository.findByGameRoomAndUserIdForUpdate(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));
            given(stockInfoRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(stockInfo));
            given(stockDailyRepository.findByStockInfoAndTradeDate(stockInfo, TEST_TRADE_DATE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(
                    TEST_ROOM_ID, TEST_USER_ID, new OrderCommand(TEST_SYMBOL, OrderType.BUY, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_STOCK_PRICE_NOT_FOUND);
        }
    }

    // === 헬퍼 메서드 ===

    private void setupCommonMocks(GameRoom room, GameTurn turn,
                                   GameParticipant participant, StockInfo stockInfo, StockDaily stockDaily) {
        given(gameRoomRepository.findById(TEST_ROOM_ID)).willReturn(Optional.of(room));
        given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                .willReturn(Optional.of(turn));
        given(gameParticipantRepository.findByGameRoomAndUserIdForUpdate(room, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(stockInfoRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(stockInfo));
        given(stockDailyRepository.findByStockInfoAndTradeDate(stockInfo, TEST_TRADE_DATE))
                .willReturn(Optional.of(stockDaily));
    }

    private GameRoom createGameRoom() {
        GameRoom room = GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, new BigDecimal("10000000"),
                6, 7, TEST_TRADE_DATE, TEST_TRADE_DATE.plusMonths(6));
        room.start();
        return room;
    }

    private GameRoom createWaitingRoom() {
        return GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, new BigDecimal("10000000"),
                6, 7, TEST_TRADE_DATE, TEST_TRADE_DATE.plusMonths(6));
    }

    private GameTurn createGameTurn(GameRoom room) {
        return GameTurn.createFirst(room);
    }

    private GameParticipant createParticipant(GameRoom room, BigDecimal seed) {
        GameParticipant participant = GameParticipant.createLeader(room, TEST_USER_ID);
        participant.assignSeed(seed);
        return participant;
    }

    private StockInfo createStockInfo() {
        return StockInfo.create(TEST_SYMBOL, "삼성전자", "KOSPI", "전기전자");
    }

    private StockDaily createStockDaily(StockInfo stockInfo, BigDecimal closePrice) {
        return StockDaily.create(stockInfo, TEST_TRADE_DATE,
                closePrice, closePrice, closePrice, closePrice,
                BigDecimal.valueOf(1000000), BigDecimal.ZERO);
    }
}
