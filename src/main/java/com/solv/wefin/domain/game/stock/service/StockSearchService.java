package com.solv.wefin.domain.game.stock.service;

import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.entity.TurnStatus;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockSearchService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final StockDailyRepository stockDailyRepository;

    public List<StockDaily> searchStocks(UUID roomId, UUID userId, String keyword) {
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        GameTurn activeTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String likeKeyword = "%" + escaped + "%";
        return stockDailyRepository.searchByKeywordAndTradeDate(likeKeyword, activeTurn.getTurnDate());

    }
}

/**
 종목 검색 : 날짜 필요 -> 게임룸 검색 -> 턴 조회 -> 날짜 획득

 1. 게임방 확인
 2. 턴조회 (ACITVE)
 3. 종목 검색 = keyword로 ** 턴 날짜에 거래 데이터 없으면 제외
 */
