package com.solv.wefin.domain.game.result.service;

import com.solv.wefin.domain.game.news.entity.BriefingCache;
import com.solv.wefin.domain.game.news.repository.BriefingCacheRepository;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.AnalysisContext;
import com.solv.wefin.domain.game.openai.OpenAiAnalysisReportClient.AnalysisParts;
import com.solv.wefin.domain.game.order.entity.GameOrder;
import com.solv.wefin.domain.game.order.entity.OrderType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameAnalysisReportServiceTest {

    @InjectMocks
    private GameAnalysisReportService service;

    @Mock private GameRoomRepository gameRoomRepository;
    @Mock private GameParticipantRepository gameParticipantRepository;
    @Mock private GameTurnRepository gameTurnRepository;
    @Mock private BriefingCacheRepository briefingCacheRepository;
    @Mock private GameOrderRepository gameOrderRepository;
    @Mock private GameResultRepository gameResultRepository;
    @Mock private GameAnalysisReportRepository analysisReportRepository;
    @Mock private OpenAiAnalysisReportClient analysisReportClient;
    @Mock private PlatformTransactionManager transactionManager;

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final Long GROUP_ID = 1L;
    private static final LocalDate START_DATE = LocalDate.of(2022, 3, 2);
    private static final LocalDate END_DATE = LocalDate.of(2022, 9, 2);
    private static final BigDecimal SEED = new BigDecimal("10000000");

    @BeforeEach
    void setUp() {
        // @PostConstruct가 단위 테스트에서는 자동 호출되지 않으므로 수동 초기화
        service.initTransactionTemplates();
    }

    @Nested
    @DisplayName("AI 분석 리포트 조회/생성 성공")
    class SuccessTests {

        @Test
        @DisplayName("캐시 히트 — DB에 이미 리포트 있으면 OpenAI 호출 안 됨")
        void cacheHit_returnsImmediately_noOpenAiCall() {
            // Given
            GameRoom room = createFinishedRoom();
            GameParticipant participant = createFinishedParticipant(room);
            GameAnalysisReport cached = createSavedReport(participant,
                    "캐시된 성과", "캐시된 패턴", "캐시된 제안");

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.findByParticipant(participant))
                    .willReturn(Optional.of(cached));

            // When
            AnalysisReportInfo result = service.getOrGenerateReport(ROOM_ID, USER_ID);

            // Then
            assertThat(result.performance()).isEqualTo("캐시된 성과");
            assertThat(result.pattern()).isEqualTo("캐시된 패턴");
            assertThat(result.suggestion()).isEqualTo("캐시된 제안");
            assertThat(result.generatedAt()).isNotNull();

            verify(analysisReportClient, never()).generateReport(any());
            verify(analysisReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("캐시 미스 — OpenAI 호출 후 DB INSERT, 컨텍스트가 제대로 빌드됨")
        void cacheMiss_callsOpenAi_savesReport() {
            // Given
            GameRoom room = createFinishedRoom();
            GameParticipant participant = createFinishedParticipant(room);
            GameResult gameResult = GameResult.create(room, participant, 1, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 5);

            GameTurn turn1 = GameTurn.createFirst(room);
            GameTurn turn2 = GameTurn.createNext(turn1, START_DATE.plusDays(1));
            BriefingCache briefing1 = BriefingCache.create(START_DATE, "1일차 시장개요", "1일차 이슈", "1일차 힌트");

            GameOrder order = mock(GameOrder.class);
            given(order.getTurn()).willReturn(turn1);
            given(order.getStockName()).willReturn("삼성전자");
            given(order.getOrderType()).willReturn(OrderType.BUY);
            given(order.getQuantity()).willReturn(10);
            given(order.getOrderPrice()).willReturn(new BigDecimal("70000"));

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.findByParticipant(participant))
                    .willReturn(Optional.empty());
            given(gameResultRepository.findByParticipant(participant))
                    .willReturn(Optional.of(gameResult));
            given(gameTurnRepository.findByGameRoomOrderByTurnNumberAsc(room))
                    .willReturn(List.of(turn1, turn2));
            given(briefingCacheRepository.findByTargetDateIn(anyCollection()))
                    .willReturn(List.of(briefing1));
            given(gameOrderRepository.findByParticipantOrderByTurnNumber(participant))
                    .willReturn(List.of(order));

            // OpenAI 응답
            given(analysisReportClient.generateReport(any(AnalysisContext.class)))
                    .willReturn(new AnalysisParts("OpenAI 성과", "OpenAI 패턴", "OpenAI 제안"));

            // 저장된 리포트 (createdAt 자동 세팅 시뮬레이션)
            given(gameParticipantRepository.findById(participant.getParticipantId()))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.save(any(GameAnalysisReport.class)))
                    .willAnswer(invocation -> {
                        GameAnalysisReport entity = invocation.getArgument(0);
                        ReflectionTestUtils.setField(entity, "createdAt", OffsetDateTime.now());
                        return entity;
                    });

            // When
            AnalysisReportInfo result = service.getOrGenerateReport(ROOM_ID, USER_ID);

            // Then
            assertThat(result.performance()).isEqualTo("OpenAI 성과");
            assertThat(result.pattern()).isEqualTo("OpenAI 패턴");
            assertThat(result.suggestion()).isEqualTo("OpenAI 제안");
            assertThat(result.generatedAt()).isNotNull();

            verify(analysisReportClient).generateReport(any(AnalysisContext.class));
            verify(analysisReportRepository).save(any(GameAnalysisReport.class));
        }

        @Test
        @DisplayName("동시 INSERT race — DataIntegrityViolation 잡고 기존 캐시 재조회")
        void concurrentInsert_recoversFromCachedRow() {
            // Given
            GameRoom room = createFinishedRoom();
            GameParticipant participant = createFinishedParticipant(room);
            GameResult gameResult = GameResult.create(room, participant, 1, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 5);
            GameTurn turn = GameTurn.createFirst(room);

            // 다른 스레드/인스턴스가 먼저 INSERT 한 결과
            GameAnalysisReport raceWinnerReport = createSavedReport(participant,
                    "레이스 위너 성과", "레이스 위너 패턴", "레이스 위너 제안");

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.findByParticipant(participant))
                    .willReturn(Optional.empty())  // lookupOrBuildContext 호출 시 캐시 없음
                    .willReturn(Optional.empty())  // 락 안에서 재조회 시에도 없음
                    .willReturn(Optional.of(raceWinnerReport));  // 충돌 후 재조회 시 있음
            given(gameResultRepository.findByParticipant(participant))
                    .willReturn(Optional.of(gameResult));
            given(gameTurnRepository.findByGameRoomOrderByTurnNumberAsc(room))
                    .willReturn(List.of(turn));
            given(briefingCacheRepository.findByTargetDateIn(anyCollection()))
                    .willReturn(List.of());
            given(gameOrderRepository.findByParticipantOrderByTurnNumber(participant))
                    .willReturn(List.of());
            given(analysisReportClient.generateReport(any(AnalysisContext.class)))
                    .willReturn(new AnalysisParts("내 성과", "내 패턴", "내 제안"));
            given(gameParticipantRepository.findById(participant.getParticipantId()))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.save(any(GameAnalysisReport.class)))
                    .willThrow(new DataIntegrityViolationException("UNIQUE 위반"));

            // When
            AnalysisReportInfo result = service.getOrGenerateReport(ROOM_ID, USER_ID);

            // Then — 충돌 후 재조회한 캐시(레이스 위너) 반환
            assertThat(result.performance()).isEqualTo("레이스 위너 성과");
            assertThat(result.pattern()).isEqualTo("레이스 위너 패턴");
            assertThat(result.suggestion()).isEqualTo("레이스 위너 제안");
        }
    }

    @Nested
    @DisplayName("AI 분석 리포트 조회/생성 실패")
    class FailTests {

        @Test
        @DisplayName("방이 없으면 ROOM_NOT_FOUND")
        void roomNotFound() {
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrGenerateReport(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROOM_NOT_FOUND);

            verify(analysisReportClient, never()).generateReport(any());
        }

        @Test
        @DisplayName("참가자 자체가 없으면 PARTICIPANT_NOT_FINISHED")
        void participantNotFound() {
            GameRoom room = createFinishedRoom();
            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrGenerateReport(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);
        }

        @Test
        @DisplayName("참가자가 FINISHED 아니면 PARTICIPANT_NOT_FINISHED")
        void participantNotFinished() {
            GameRoom room = createFinishedRoom();
            GameParticipant active = GameParticipant.createLeader(room, USER_ID); // ACTIVE 상태

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(active));

            assertThatThrownBy(() -> service.getOrGenerateReport(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);
        }

        @Test
        @DisplayName("FINISHED인데 GameResult가 없으면 PARTICIPANT_NOT_FINISHED (데이터 불일치 방어)")
        void gameResultMissing() {
            GameRoom room = createFinishedRoom();
            GameParticipant participant = createFinishedParticipant(room);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.findByParticipant(participant))
                    .willReturn(Optional.empty());
            given(gameResultRepository.findByParticipant(participant))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrGenerateReport(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NOT_FINISHED);

            verify(analysisReportClient, never()).generateReport(any());
        }

        @Test
        @DisplayName("OpenAI 호출 실패 시 ANALYSIS_GENERATION_FAILED 그대로 전파")
        void openAiCallFailed() {
            GameRoom room = createFinishedRoom();
            GameParticipant participant = createFinishedParticipant(room);
            GameResult gameResult = GameResult.create(room, participant, 1, SEED,
                    new BigDecimal("12000000"), new BigDecimal("20.00"), 5);
            GameTurn turn = GameTurn.createFirst(room);

            given(gameRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(gameParticipantRepository.findByGameRoomAndUserId(room, USER_ID))
                    .willReturn(Optional.of(participant));
            given(analysisReportRepository.findByParticipant(participant))
                    .willReturn(Optional.empty());
            given(gameResultRepository.findByParticipant(participant))
                    .willReturn(Optional.of(gameResult));
            given(gameTurnRepository.findByGameRoomOrderByTurnNumberAsc(room))
                    .willReturn(List.of(turn));
            given(briefingCacheRepository.findByTargetDateIn(anyCollection()))
                    .willReturn(List.of());
            given(gameOrderRepository.findByParticipantOrderByTurnNumber(participant))
                    .willReturn(List.of());
            given(analysisReportClient.generateReport(any(AnalysisContext.class)))
                    .willThrow(new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED));

            assertThatThrownBy(() -> service.getOrGenerateReport(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANALYSIS_GENERATION_FAILED);

            verify(analysisReportRepository, never()).save(any());
        }
    }

    // === 헬퍼 메서드 ===

    private GameRoom createFinishedRoom() {
        GameRoom room = GameRoom.create(GROUP_ID, USER_ID, SEED,
                6, 7, START_DATE, END_DATE);
        room.start();
        room.finish();
        return room;
    }

    private GameParticipant createFinishedParticipant(GameRoom room) {
        GameParticipant participant = GameParticipant.createLeader(room, USER_ID);
        participant.finish();
        // 실제 환경에서는 JPA가 @GeneratedValue로 PK를 채우지만, 단위 테스트에서는
        // ConcurrentHashMap 락 키로 쓰이는 participantId가 null이면 NPE가 나므로 강제 세팅
        ReflectionTestUtils.setField(participant, "participantId",
                UUID.fromString("00000000-0000-4000-a000-000000000099"));
        return participant;
    }

    /** 캐시 히트 시뮬레이션용 — createdAt까지 채워서 반환 */
    private GameAnalysisReport createSavedReport(GameParticipant participant,
                                                 String performance,
                                                 String pattern,
                                                 String suggestion) {
        GameAnalysisReport report = GameAnalysisReport.create(participant, performance, pattern, suggestion);
        ReflectionTestUtils.setField(report, "createdAt", OffsetDateTime.now());
        return report;
    }
}
