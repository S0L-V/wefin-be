package com.solv.wefin.domain.game.turn.service;

import com.solv.wefin.domain.game.holding.entity.GameHolding;
import com.solv.wefin.domain.game.holding.repository.GameHoldingRepository;
import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.entity.RoomStatus;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.snapshot.entity.GamePortfolioSnapshot;
import com.solv.wefin.domain.game.snapshot.repository.GamePortfolioSnapshotRepository;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.domain.game.turn.event.TurnChangeEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnAdvanceServiceTest {

    @InjectMocks
    private TurnAdvanceService turnAdvanceService;

    @Mock
    private GameRoomRepository gameRoomRepository;
    @Mock
    private GameParticipantRepository gameParticipantRepository;
    @Mock
    private GameTurnRepository gameTurnRepository;
    @Mock
    private GameHoldingRepository gameHoldingRepository;
    @Mock
    private StockDailyRepository stockDailyRepository;
    @Mock
    private GamePortfolioSnapshotRepository snapshotRepository;
    @Mock
    private BriefingCacheRepository briefingCacheRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static final UUID TEST_ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final Long TEST_GROUP_ID = 1L;
    private static final BigDecimal SEED_MONEY = new BigDecimal("10000000");
    private static final LocalDate START_DATE = LocalDate.of(2022, 3, 2);

    // === 성공 케이스 ===

    @Nested
    @DisplayName("턴 전환 성공")
    class AdvanceSuccess {

        @Test
        @DisplayName("보유종목 없이 턴 전환 — 현금만으로 스냅샷 생성 + 새 턴")
        void advance_noHoldings_success() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn currentTurn = GameTurn.createFirst(room);
            GameParticipant participant = createParticipant(room);
            LocalDate nextTradeDate = START_DATE.plusDays(7);

            setupCommonMocks(room, currentTurn, participant);
            given(gameHoldingRepository.findAllByParticipantAndQuantityGreaterThan(participant, 0))
                    .willReturn(List.of());
            given(stockDailyRepository.findLatestTradeDateOnOrBefore(START_DATE.plusDays(7)))
                    .willReturn(Optional.of(nextTradeDate));
            given(briefingCacheRepository.findByTargetDate(nextTradeDate))
                    .willReturn(Optional.empty());
            given(gameTurnRepository.save(any(GameTurn.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            GameTurn result = turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTurnNumber()).isEqualTo(2);
            assertThat(result.getTurnDate()).isEqualTo(nextTradeDate);
            assertThat(result.getStatus()).isEqualTo(TurnStatus.ACTIVE);
            assertThat(currentTurn.getStatus()).isEqualTo(TurnStatus.COMPLETED);

            // 스냅샷 저장 검증
            ArgumentCaptor<GamePortfolioSnapshot> captor = ArgumentCaptor.forClass(GamePortfolioSnapshot.class);
            verify(snapshotRepository).save(captor.capture());
            GamePortfolioSnapshot snapshot = captor.getValue();
            assertThat(snapshot.getCash()).isEqualByComparingTo(SEED_MONEY);
            assertThat(snapshot.getStockValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(snapshot.getTotalAsset()).isEqualByComparingTo(SEED_MONEY);
            assertThat(snapshot.getProfitRate()).isEqualByComparingTo(BigDecimal.ZERO);

            // 이벤트 발행 검증
            ArgumentCaptor<TurnChangeEvent> eventCaptor = ArgumentCaptor.forClass(TurnChangeEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            TurnChangeEvent event = eventCaptor.getValue();
            assertThat(event.roomId()).isEqualTo(TEST_ROOM_ID);
            assertThat(event.turnNumber()).isEqualTo(2);
            assertThat(event.turnDate()).isEqualTo(nextTradeDate);
            assertThat(event.snapshots()).hasSize(1);
        }

        @Test
        @DisplayName("보유종목 있는 턴 전환 — 종가 평가 포함 스냅샷")
        void advance_withHoldings_evaluatesAtClosePrice() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn currentTurn = GameTurn.createFirst(room);
            GameParticipant participant = createParticipant(room);
            participant.deductCash(new BigDecimal("555000")); // 매수로 현금 차감
            StockInfo stockInfo = StockInfo.create("005930", "삼성전자", "KOSPI", "전기전자");
            GameHolding holding = GameHolding.create(participant, stockInfo, 10, new BigDecimal("55500"));
            StockDaily daily = StockDaily.create(stockInfo, START_DATE,
                    new BigDecimal("60000"), new BigDecimal("60000"),
                    new BigDecimal("60000"), new BigDecimal("60000"),
                    BigDecimal.valueOf(1000000), BigDecimal.ZERO);
            LocalDate nextTradeDate = START_DATE.plusDays(7);

            setupCommonMocks(room, currentTurn, participant);
            given(gameHoldingRepository.findAllByParticipantAndQuantityGreaterThan(participant, 0))
                    .willReturn(List.of(holding));
            given(stockDailyRepository.findAllByStockInfoInAndTradeDate(List.of(stockInfo), START_DATE))
                    .willReturn(List.of(daily));
            given(stockDailyRepository.findLatestTradeDateOnOrBefore(START_DATE.plusDays(7)))
                    .willReturn(Optional.of(nextTradeDate));
            given(briefingCacheRepository.findByTargetDate(nextTradeDate))
                    .willReturn(Optional.empty());
            given(gameTurnRepository.save(any(GameTurn.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID);

            // Then — 종가 60000 × 10주 = 600000
            ArgumentCaptor<GamePortfolioSnapshot> captor = ArgumentCaptor.forClass(GamePortfolioSnapshot.class);
            verify(snapshotRepository).save(captor.capture());
            GamePortfolioSnapshot snapshot = captor.getValue();
            assertThat(snapshot.getStockValue()).isEqualByComparingTo(new BigDecimal("600000"));
            BigDecimal expectedCash = SEED_MONEY.subtract(new BigDecimal("555000"));
            assertThat(snapshot.getCash()).isEqualByComparingTo(expectedCash);
            assertThat(snapshot.getTotalAsset()).isEqualByComparingTo(expectedCash.add(new BigDecimal("600000")));
        }

        @Test
        @DisplayName("브리핑 있는 날짜로 전환 — briefingId 연결")
        void advance_withBriefing_assignsBriefingId() {
            // Given
            GameRoom room = createGameRoom();
            GameTurn currentTurn = GameTurn.createFirst(room);
            GameParticipant participant = createParticipant(room);
            LocalDate nextTradeDate = START_DATE.plusDays(7);
            BriefingCache briefing = BriefingCache.create(nextTradeDate, "개요", "이슈", "힌트");

            setupCommonMocks(room, currentTurn, participant);
            given(gameHoldingRepository.findAllByParticipantAndQuantityGreaterThan(participant, 0))
                    .willReturn(List.of());
            given(stockDailyRepository.findLatestTradeDateOnOrBefore(START_DATE.plusDays(7)))
                    .willReturn(Optional.of(nextTradeDate));
            given(briefingCacheRepository.findByTargetDate(nextTradeDate))
                    .willReturn(Optional.of(briefing));
            given(gameTurnRepository.save(any(GameTurn.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            GameTurn result = turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID);

            // Then
            assertThat(result.getBriefingId()).isEqualTo(briefing.getBriefingId());
        }

        @Test
        @DisplayName("endDate 초과 — 게임 종료, null 반환")
        void advance_exceedsEndDate_finishesGame() {
            // Given — endDate가 START_DATE + 7일 이내
            GameRoom room = GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, SEED_MONEY,
                    1, 7, START_DATE, START_DATE.plusDays(5));
            room.start();
            GameTurn currentTurn = GameTurn.createFirst(room);
            GameParticipant participant = createParticipant(room);
            LocalDate nextTradeDate = START_DATE.plusDays(7); // endDate 초과

            given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));
            given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                    .willReturn(Optional.of(currentTurn));
            given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                    .willReturn(List.of(participant));
            given(gameHoldingRepository.findAllByParticipantAndQuantityGreaterThan(participant, 0))
                    .willReturn(List.of());
            given(stockDailyRepository.findLatestTradeDateOnOrBefore(START_DATE.plusDays(7)))
                    .willReturn(Optional.of(nextTradeDate));

            // When
            GameTurn result = turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID);

            // Then
            assertThat(result).isNull();
            assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
            assertThat(currentTurn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
            verify(gameTurnRepository, never()).save(any(GameTurn.class));
            verify(eventPublisher, never()).publishEvent(any(TurnChangeEvent.class));
        }
    }

    // === 실패 케이스 ===

    @Nested
    @DisplayName("검증 실패")
    class ValidationTests {

        @Test
        @DisplayName("실패 — 방이 없음")
        void fail_roomNotFound() {
            given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 — 이미 종료된 게임")
        void fail_gameAlreadyFinished() {
            GameRoom room = createGameRoom();
            room.finish();
            given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.of(room));

            assertThatThrownBy(() -> turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_ALREADY_FINISHED);
        }

        @Test
        @DisplayName("실패 — 참가자 아님")
        void fail_notParticipant() {
            GameRoom room = createGameRoom();
            given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("실패 — 활성 턴 없음")
        void fail_noActiveTurn() {
            GameRoom room = createGameRoom();
            GameParticipant participant = createParticipant(room);
            given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                    .willReturn(Optional.of(participant));
            given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> turnAdvanceService.advanceTurn(TEST_ROOM_ID, TEST_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_STARTED);
        }
    }

    // === 헬퍼 메서드 ===

    private void setupCommonMocks(GameRoom room, GameTurn turn, GameParticipant participant) {
        given(gameRoomRepository.findByIdForUpdate(TEST_ROOM_ID)).willReturn(Optional.of(room));
        given(gameParticipantRepository.findByGameRoomAndUserId(room, TEST_USER_ID))
                .willReturn(Optional.of(participant));
        given(gameTurnRepository.findByGameRoomAndStatus(room, TurnStatus.ACTIVE))
                .willReturn(Optional.of(turn));
        given(gameParticipantRepository.findByGameRoomAndStatus(room, ParticipantStatus.ACTIVE))
                .willReturn(List.of(participant));
    }

    private GameRoom createGameRoom() {
        GameRoom room = GameRoom.create(TEST_GROUP_ID, TEST_USER_ID, SEED_MONEY,
                6, 7, START_DATE, START_DATE.plusMonths(6));
        room.start();
        return room;
    }

    private GameParticipant createParticipant(GameRoom room) {
        GameParticipant participant = GameParticipant.createLeader(room, TEST_USER_ID);
        participant.assignSeed(SEED_MONEY);
        return participant;
    }
}
