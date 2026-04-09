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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockSearchService {

    /**
     * 키워드 검색 결과 개수 상한.
     * 짧은 키워드(1~2자)에서 수천 건이 반환되어 네트워크/응답 시간이 폭주하는 걸 방지.
     * 50개면 자동완성/드롭다운 용도로 충분.
     */
    private static final int MAX_SEARCH_RESULTS = 50;

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final StockDailyRepository stockDailyRepository;

    public List<StockDaily> searchStocks(UUID roomId, UUID userId, String keyword) {
        // 공백/빈 문자열 조기 반환.
        // 그대로 LIKE 쿼리에 넣으면 "%   %"나 "%%"가 되어 거의 전체 종목이 매칭됨.
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        // 1. 게임방 존재 확인
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참가자 검증
        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 3. ACTIVE 턴 조회 → 턴 날짜 획득
        GameTurn activeTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        // 4. keyword로 종목 검색 (턴 날짜에 거래 데이터 있는 것만, 최대 MAX_SEARCH_RESULTS건)
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String likeKeyword = "%" + escaped + "%";
        Pageable pageable = PageRequest.of(0, MAX_SEARCH_RESULTS);
        return stockDailyRepository.searchByKeywordAndTradeDate(likeKeyword, activeTurn.getTurnDate(), pageable);
    }
}

/**
 종목 검색 : 날짜 필요 -> 게임룸 검색 -> 턴 조회 -> 날짜 획득

 1. 게임방 확인
 2. 턴조회 (ACITVE)
 3. 종목 검색 = keyword로 ** 턴 날짜에 거래 데이터 없으면 제외
 */
