package com.solv.wefin.domain.game.turn.service;

import com.solv.wefin.domain.game.holding.entity.GameHolding;
import com.solv.wefin.domain.game.holding.repository.GameHoldingRepository;
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
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.domain.game.turn.event.TurnChangeEvent;
import com.solv.wefin.domain.game.turn.event.TurnChangeEvent.SnapshotData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnAdvanceService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameHoldingRepository gameHoldingRepository;
    private final StockDailyRepository stockDailyRepository;
    private final GamePortfolioSnapshotRepository snapshotRepository;
    private final BriefingCacheRepository briefingCacheRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 턴 전환 핵심 로직.
     * 투표 통과, REST 직접 호출 등 어디서든 이 메서드를 호출하면 턴이 전환된다.
     *
     * @return 새로 생성된 턴 (게임 종료 시 null)
     */
    @Transactional
    public GameTurn advanceTurn(UUID roomId, UUID userId) {
        // 1. 게임방 조회 + 상태 검증 (비관적 락)
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (gameRoom.getStatus() == RoomStatus.FINISHED) {
            throw new BusinessException(ErrorCode.GAME_ALREADY_FINISHED);
        }

        // 2. 참가자 검증
        gameParticipantRepository.findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_PARTICIPANT));

        // 3. 현재 활성 턴 조회
        GameTurn currentTurn = gameTurnRepository.findByGameRoomAndStatus(gameRoom, TurnStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_STARTED));

        // 4. 모든 활성 참가자의 보유종목 평가 + 스냅샷 저장
        List<GameParticipant> activeParticipants =
                gameParticipantRepository.findByGameRoomAndStatus(gameRoom, ParticipantStatus.ACTIVE);

        List<GamePortfolioSnapshot> snapshots =
                saveSnapshotsForAll(currentTurn, activeParticipants, gameRoom.getSeed());

        // 5. 현재 턴 완료 처리
        currentTurn.complete();

        // 6. 다음 거래일 계산
        LocalDate nextDate = calculateNextTradeDate(currentTurn.getTurnDate(), gameRoom.getMoveDays());

        // 7. 종료 판단: 다음 날짜가 endDate 초과 시 게임 종료
        if (nextDate.isAfter(gameRoom.getEndDate())) {
            gameRoom.finish();
            log.info("[턴 전환] 게임 종료: roomId={}, 마지막 턴={}", roomId, currentTurn.getTurnNumber());
            return null;
        }

        // 8. 새 턴 생성
        GameTurn nextTurn = GameTurn.createNext(currentTurn, nextDate);

        // 9. 브리핑 연결
        briefingCacheRepository.findByTargetDate(nextDate)
                .ifPresent(briefing -> nextTurn.assignBriefing(briefing.getBriefingId()));

        gameTurnRepository.save(nextTurn);
        log.info("[턴 전환] 새 턴 생성: roomId={}, turn={}, date={}", roomId, nextTurn.getTurnNumber(), nextDate);

        // 10. 턴 전환 이벤트 발행 (커밋 후 WebSocket 브로드캐스트)
        // 트랜잭션 안에서 Entity → SnapshotData 변환 (AFTER_COMMIT 시 Lazy Loading 방지)
        List<SnapshotData> snapshotDataList = snapshots.stream()
                .map(s -> new SnapshotData(
                        s.getParticipant().getUserId(),
                        s.getTotalAsset(),
                        s.getProfitRate()))
                .toList();

        eventPublisher.publishEvent(new TurnChangeEvent(
                roomId, nextTurn.getTurnNumber(), nextDate,
                nextTurn.getBriefingId(), snapshotDataList));

        return nextTurn;
    }

    /**
     * 모든 활성 참가자의 포트폴리오 스냅샷을 저장한다.
     * 보유종목이 없는 참가자도 현금만으로 스냅샷을 생성한다.
     */
    private List<GamePortfolioSnapshot> saveSnapshotsForAll(GameTurn turn,
                                                             List<GameParticipant> participants,
                                                             BigDecimal seedMoney) {
        LocalDate turnDate = turn.getTurnDate();
        List<GamePortfolioSnapshot> savedSnapshots = new ArrayList<>();

        for (GameParticipant participant : participants) {
            List<GameHolding> holdings =
                    gameHoldingRepository.findAllByParticipantAndQuantityGreaterThan(participant, 0);

            BigDecimal stockValue = evaluateHoldings(holdings, turnDate);

            GamePortfolioSnapshot snapshot = GamePortfolioSnapshot.create(
                    turn, participant, participant.getSeed(), stockValue, seedMoney);

            savedSnapshots.add(snapshotRepository.save(snapshot));
        }

        return savedSnapshots;
    }

    /**
     * 보유종목을 해당 날짜 종가로 평가한다.
     * 종가 데이터가 없는 종목은 가장 가까운 이전 거래일의 종가를 사용한다.
     */
    private BigDecimal evaluateHoldings(List<GameHolding> holdings, LocalDate turnDate) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 보유종목의 StockInfo 목록 추출
        List<StockInfo> stockInfos = holdings.stream()
                .map(GameHolding::getStockInfo)
                .toList();

        // 해당 날짜 종가 일괄 조회 (N+1 방지)
        Map<StockInfo, StockDaily> priceMap = stockDailyRepository
                .findAllByStockInfoInAndTradeDate(stockInfos, turnDate)
                .stream()
                .collect(Collectors.toMap(StockDaily::getStockInfo, Function.identity()));

        BigDecimal totalValue = BigDecimal.ZERO;
        for (GameHolding holding : holdings) {
            StockDaily daily = priceMap.get(holding.getStockInfo());

            BigDecimal closePrice;
            if (daily != null) {
                closePrice = daily.getClosePrice();
            } else {
                // 비거래일: 가장 가까운 이전 거래일 종가 사용
                closePrice = findFallbackClosePrice(holding.getStockInfo(), turnDate);
            }

            totalValue = totalValue.add(closePrice.multiply(BigDecimal.valueOf(holding.getQuantity())));
        }

        return totalValue;
    }

    private BigDecimal findFallbackClosePrice(StockInfo stockInfo, LocalDate date) {
        return stockDailyRepository.findLatestTradeDateOnOrBefore(date)
                .flatMap(tradeDate -> stockDailyRepository.findByStockInfoAndTradeDate(stockInfo, tradeDate))
                .map(StockDaily::getClosePrice)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 다음 거래일을 계산한다.
     * 현재 날짜 + moveDays 후, 비거래일이면 가장 가까운 이전 거래일로 보정.
     */
    private LocalDate calculateNextTradeDate(LocalDate currentDate, int moveDays) {
        LocalDate targetDate = currentDate.plusDays(moveDays);

        return stockDailyRepository.findLatestTradeDateOnOrBefore(targetDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_STOCK_PRICE_NOT_FOUND));
    }
}
