package com.solv.wefin.domain.game.stock.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockChartService {

    private final GameRoomRepository gameRoomRepository;
    private final GameTurnRepository gameTurnRepository;
    private final StockInfoRepository stockInfoRepository;
    private final StockDailyRepository stockDailyRepository;

    public List<StockDaily> getChart(String symbol, UUID roomId) {
        // 1. 게임방 확인
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. ACTIVE 턴 → 턴 날짜
        GameTurn activeTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        // 3. 종목 존재 확인
        StockInfo stockInfo = stockInfoRepository.findById(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_STOCK_NOT_FOUND));

        // 4. 턴 날짜 기준 2년치 일봉 조회
        LocalDate turnDate = activeTurn.getTurnDate();
        LocalDate twoYearsAgo = turnDate.minusYears(2);
        return stockDailyRepository.findByStockInfoAndTradeDateBetweenOrderByTradeDateAsc(
                stockInfo, twoYearsAgo, turnDate);
    }
}
