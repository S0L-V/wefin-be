package com.solv.wefin.domain.game.stock.service;

import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final StockInfoRepository stockInfoRepository;

    public List<StockDaily> searchStocks(UUID roomId, UUID userId, String keyword) {
        // 공백/빈 문자열 조기 반환.
        // 그대로 LIKE 쿼리에 넣으면 "%   %"나 "%%"가 되어 거의 전체 종목이 매칭됨.
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        // 1. 방 + 참가자 검증
        GameRoom gameRoom = validateParticipant(roomId, userId);

        // 2. ACTIVE 턴 조회 → 턴 날짜 획득
        GameTurn activeTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        // 3. keyword로 종목 검색 (턴 날짜에 거래 데이터 있는 것만, 최대 MAX_SEARCH_RESULTS건)
        String escaped = trimmed.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String likeKeyword = "%" + escaped + "%";
        Pageable pageable = PageRequest.of(0, MAX_SEARCH_RESULTS);
        return stockDailyRepository.searchByKeywordAndTradeDate(likeKeyword, activeTurn.getTurnDate(), pageable);
    }
    /** 섹터 목록 + 키워드 개수 조회 */
    public List<SectorKeywordCount> getSectors(UUID roomId, UUID userId) {
        validateParticipant(roomId, userId);
        return stockInfoRepository.findSectorsWithKeywordCount();
    }

    /** 특정 섹터의 키워드 목록 조회 */
    public List<String> getKeywords(UUID roomId, UUID userId, String sector) {
        if (sector == null || sector.isBlank()) {
            return List.of();
        }
        validateParticipant(roomId, userId);
        return stockInfoRepository.findKeywordsBySector(sector.trim());
    }

    /** 특정 섹터+키워드의 종목 목록 조회 (현재 턴 종가 포함) */
    public List<StockDaily> getStocksByKeyword(UUID roomId, UUID userId, String sector, String keyword) {
        if (sector == null || sector.isBlank() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        GameRoom gameRoom = validateParticipant(roomId, userId);

        GameTurn activeTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        LocalDate turnDate = activeTurn.getTurnDate();

        // 해당 섹터+키워드 종목 목록 조회 (이름순)
        List<StockInfo> stockInfos = stockInfoRepository
                .findBySectorAndKeywordOrderByStockNameAsc(sector, keyword);

        if (stockInfos.isEmpty()) {
            return List.of();
        }

        // 턴 날짜 종가 일괄 조회 (N+1 방지)
        Map<StockInfo, StockDaily> priceMap = stockDailyRepository
                .findAllByStockInfoInAndTradeDate(stockInfos, turnDate)
                .stream()
                .collect(Collectors.toMap(StockDaily::getStockInfo, Function.identity()));

        // 종가 데이터가 있는 종목만 반환 (이름순 유지)
        return stockInfos.stream()
                .filter(priceMap::containsKey)
                .map(priceMap::get)
                .toList();
    }

    /** 방 존재 + 참가자 검증 공통 메서드 */
    private GameRoom validateParticipant(UUID roomId, UUID userId) {
        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        return gameRoom;
    }
}
