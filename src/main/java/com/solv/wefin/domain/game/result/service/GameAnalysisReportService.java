package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.AnalysisContext;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.AnalysisParts;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.DailyContext;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.TradeSummary;
import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.order.repository.GameOrderRepository;
import com.solv.wefin.domain.game.participant.entity.GameParticipant;
import com.solv.wefin.domain.game.participant.entity.ParticipantStatus;
import com.solv.wefin.domain.game.participant.repository.GameParticipantRepository;
import com.solv.wefin.domain.game.result.dto.AnalysisReportInfo;
import com.solv.wefin.domain.game.result.entity.GameAnalysisReport;
import com.solv.wefin.domain.game.result.entity.GameResult;
import com.solv.wefin.domain.game.result.repository.GameAnalysisReportRepository;
import com.solv.wefin.domain.game.result.repository.GameResultRepository;
import com.solv.wefin.domain.game.room.entity.GameRoom;
import com.solv.wefin.domain.game.room.repository.GameRoomRepository;
import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.repository.GameTurnRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 최종 분석 리포트 lazy 생성 + 캐시 조회.
 *
 * 트랜잭션 분리 정책:
 *  - readOnly TX: 검증 + 캐시 조회 + 컨텍스트 빌드 (짧음)
 *  - 트랜잭션 밖: OpenAI 호출 (5~10초, DB 커넥션을 들고 있으면 안 됨)
 *  - write TX: INSERT만 (짧음)
 *
 * 동시성:
 *  - participantId in-process 락: 같은 참가자의 multi-tab 동시 호출 직렬화
 *  - DB UNIQUE(participant_id) 제약: 다중 인스턴스/락 만료 사이의 race 최후 방어
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameAnalysisReportService {

    private final GameRoomRepository gameRoomRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final BriefingCacheRepository briefingCacheRepository;
    private final GameOrderRepository gameOrderRepository;
    private final GameResultRepository gameResultRepository;
    private final GameAnalysisReportRepository analysisReportRepository;
    private final OpenAiAnalysisReportClient analysisReportClient;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate readOnlyTx;
    private TransactionTemplate writeTx;

    private final ConcurrentHashMap<UUID, Object> participantLocks = new ConcurrentHashMap<>();

    @PostConstruct
    void initTransactionTemplates() {
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    public AnalysisReportInfo getOrGenerateReport(UUID roomId, UUID userId) {

        ContextResult result = readOnlyTx.execute(status -> lookupOrBuildContext(roomId, userId));
        if (result.cached() != null) {
            return result.cached();
        }

        Object lock = participantLocks.computeIfAbsent(result.participantId(), k -> new Object());
        synchronized (lock) {
            try {
                ContextResult rechecked = readOnlyTx.execute(status -> lookupOrBuildContext(roomId, userId));
                if (rechecked.cached() != null) {
                    return rechecked.cached();
                }

                AnalysisParts parts = analysisReportClient.generateReport(rechecked.context());

                return writeTx.execute(status -> saveReport(rechecked.participantId(), parts));
            } finally {
                participantLocks.remove(result.participantId(), lock);
            }
        }
    }

    private ContextResult lookupOrBuildContext(UUID roomId, UUID userId) {

        GameRoom gameRoom = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        GameParticipant participant = gameParticipantRepository
                .findByGameRoomAndUserId(gameRoom, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.FINISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        Optional<GameAnalysisReport> cached = analysisReportRepository.findByParticipant(participant);
        if (cached.isPresent()) {
            return ContextResult.cacheHit(toInfo(cached.get()), participant.getParticipantId());
        }

        GameResult result = gameResultRepository.findByParticipant(participant)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FINISHED));

        List<GameTurn> turns = gameTurnRepository.findByGameRoomOrderByTurnNumberAsc(gameRoom);
        List<LocalDate> turnDates = turns.stream().map(GameTurn::getTurnDate).distinct().toList();

        Map<LocalDate, String> overviewByDate = briefingCacheRepository.findByTargetDateIn(turnDates).stream()
                .collect(Collectors.toMap(BriefingCache::getTargetDate, BriefingCache::getMarketOverview));

        List<GameOrder> orders = gameOrderRepository.findByParticipantOrderByTurnNumber(participant);
        Map<LocalDate, List<TradeSummary>> tradesByDate = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getTurn().getTurnDate(),
                        Collectors.mapping(o -> new TradeSummary(
                                o.getStockName(),
                                o.getOrderType().name(),
                                o.getQuantity(),
                                o.getOrderPrice()
                        ), Collectors.toList())
                ));

        List<DailyContext> dailies = turns.stream()
                .map(t -> new DailyContext(
                        t.getTurnDate(),
                        overviewByDate.getOrDefault(t.getTurnDate(), "시장 데이터 없음"),
                        tradesByDate.getOrDefault(t.getTurnDate(), List.of())))
                .toList();

        AnalysisContext context = new AnalysisContext(
                gameRoom.getStartDate(),
                gameRoom.getEndDate(),
                result.getSeedMoney(),
                result.getFinalAsset(),
                result.getProfitRate(),
                dailies);

        return ContextResult.cacheMiss(context, participant.getParticipantId());
    }

    private AnalysisReportInfo saveReport(UUID participantId, AnalysisParts parts) {
        // readOnly TX에서 검증된 participantId가 write TX에서 사라지는 건 정상 흐름이 아님
        // (외부 DELETE 등 데이터 불일치). 사용자 메시지가 아니라 내부 오류로 처리한다.
        GameParticipant participant = gameParticipantRepository.findById(participantId)
                .orElseThrow(() -> {
                    log.error("[분석리포트] readOnly TX 이후 참가자 사라짐 — 데이터 불일치: participantId={}",
                            participantId);
                    return new IllegalStateException(
                            "Participant disappeared between transactions: " + participantId);
                });

        try {
            GameAnalysisReport saved = analysisReportRepository.save(
                    GameAnalysisReport.create(
                            participant,
                            parts.performance(),
                            parts.pattern(),
                            parts.suggestion()));
            return toInfo(saved);
        } catch (DataIntegrityViolationException e) {
            log.info("[분석리포트] 동시 INSERT 감지, 기존 캐시 사용: participantId={}", participantId);
            return analysisReportRepository.findByParticipant(participant)
                    .map(this::toInfo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED));
        }
    }

    private AnalysisReportInfo toInfo(GameAnalysisReport report) {
        return new AnalysisReportInfo(
                report.getPerformance(),
                report.getPattern(),
                report.getSuggestion(),
                report.getCreatedAt());
    }

    private record ContextResult(AnalysisReportInfo cached,
                                 AnalysisContext context,
                                 UUID participantId) {
        static ContextResult cacheHit(AnalysisReportInfo cached, UUID participantId) {
            return new ContextResult(cached, null, participantId);
        }
        static ContextResult cacheMiss(AnalysisContext context, UUID participantId) {
            return new ContextResult(null, context, participantId);
        }
    }
}
