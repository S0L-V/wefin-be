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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameOrderService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");   // 0.015%
    private static final BigDecimal TAX_RATE = new BigDecimal("0.0018");    // 0.18%

    private final GameRoomRepository gameRoomRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final StockInfoRepository stockInfoRepository;
    private final StockDailyRepository stockDailyRepository;
    private final GameOrderRepository gameOrderRepository;
    private final GameHoldingRepository gameHoldingRepository;

    @Transactional
    public GameOrder placeOrder(UUID roomId, UUID userId, OrderCommand command) {

        String symbol = command.symbol();
        OrderType orderType = command.orderType();
        Integer quantity = command.quantity();

        // 1. 방 조회 + 상태 확인
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (gameRoom.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED);
        }

        // 2. 현재 턴 조회 (ACTIVE 턴)
        GameTurn currentTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        // 3. 참가자 조회 + 비관적 락 (잔액 동시성 제어)
        GameParticipant participant = gameParticipantRepository
                .findByGameRoomAndUserIdForUpdate(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 4. 종목 조회
        StockInfo stockInfo = stockInfoRepository.findById(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_STOCK_NOT_FOUND));

        // 5. 턴 날짜의 종가 조회
        StockDaily stockDaily = stockDailyRepository.findByStockInfoAndTradeDate(stockInfo, currentTurn.getTurnDate())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_STOCK_PRICE_NOT_FOUND));

        BigDecimal price = stockDaily.getClosePrice();

        // 6. 매수/매도 분기
        if (orderType == OrderType.BUY) {
            return executeBuy(participant, currentTurn, stockInfo, price, quantity);
        } else {
            return executeSell(participant, currentTurn, stockInfo, price, quantity);
        }
    }

    private GameOrder executeBuy(GameParticipant participant, GameTurn turn,
                                 StockInfo stockInfo, BigDecimal price, Integer quantity) {

        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = totalPrice.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCost = totalPrice.add(fee);

        // 잔고 확인
        if (participant.getSeed().compareTo(totalCost) < 0) {
            throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_BALANCE);
        }

        // 잔고 차감
        participant.deductCash(totalCost);

        // 보유종목 갱신 (있으면 가중평균, 없으면 생성)
        Optional<GameHolding> existingHolding = gameHoldingRepository
                .findByParticipantAndStockInfo(participant, stockInfo);

        if (existingHolding.isPresent()) {
            existingHolding.get().addQuantity(quantity, price);
        } else {
            GameHolding newHolding = GameHolding.create(participant, stockInfo, quantity, price);
            gameHoldingRepository.save(newHolding);
        }

        // 주문 저장
        GameOrder order = GameOrder.createBuy(participant, turn, stockInfo, price, quantity, fee);
        return gameOrderRepository.save(order);
    }

    private GameOrder executeSell(GameParticipant participant, GameTurn turn,
                                  StockInfo stockInfo, BigDecimal price, Integer quantity) {

        // 보유종목 확인
        GameHolding holding = gameHoldingRepository.findByParticipantAndStockInfo(participant, stockInfo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_STOCK_NOT_HELD));

        // 보유 수량 확인
        if (holding.getQuantity() < quantity) {
            throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_HOLDINGS);
        }

        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = totalPrice.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = totalPrice.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProceeds = totalPrice.subtract(fee).subtract(tax);

        // 잔고 증가
        participant.addCash(netProceeds);

        // 보유종목: 전량 매도면 삭제, 일부면 수량 감소
        if (holding.getQuantity().equals(quantity)) {
            gameHoldingRepository.delete(holding);
        } else {
            holding.reduceQuantity(quantity);
        }

        // 주문 저장
        GameOrder order = GameOrder.createSell(participant, turn, stockInfo, price, quantity, fee, tax);
        return gameOrderRepository.save(order);
    }
}
