package com.solv.wefin.domain.market.trend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.solv.wefin.domain.market.trend.dto.PersonalizationMode;
import com.solv.wefin.domain.market.trend.repository.UserMarketTrendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 사용자별 맞춤 동향 캐시 쓰기/무효화 전담 (트랜잭션 경계)
 *
 * 캐시 read는 {@link PersonalizedMarketTrendService} 내부에서 직접 수행하고
 * 쓰기/삭제는 본 서비스를 통해 짧은 트랜잭션으로 처리한다 (AI 외부 호출과 분리)
 *
 * 단일 인스턴스 가정
 */
@Slf4j
@Service
public class UserMarketTrendCacheService {

    public static final ZoneId TREND_ZONE = ZoneId.of("Asia/Seoul"); // 캐시 TTL 기준 timezone

    /** invalidate 타임스탬프 캐시의 최대 엔트리 수 — 활성 사용자 상한 근사치 */
    private static final long INVALIDATION_CACHE_MAX_SIZE = 100_000L;
    /** invalidate 엔트리 만료 — 오늘자 compute 창(24h)을 덮을 만큼이면 충분 */
    private static final Duration INVALIDATION_CACHE_TTL = Duration.ofDays(1);

    private final UserMarketTrendRepository userMarketTrendRepository;
    /**
     * 사용자별 마지막 invalidate 시각. ConcurrentHashMap을 Caffeine으로 교체해 장기 운영 시
     * heap 누수를 방지한다. expireAfterWrite는 오늘자 compute가 가능한 최대 구간(=1일)과 정렬
     */
    private final Cache<UUID, Instant> lastInvalidatedAt;

    public UserMarketTrendCacheService(UserMarketTrendRepository userMarketTrendRepository) {
        this.userMarketTrendRepository = userMarketTrendRepository;
        this.lastInvalidatedAt = Caffeine.newBuilder()
                .maximumSize(INVALIDATION_CACHE_MAX_SIZE)
                .expireAfterWrite(INVALIDATION_CACHE_TTL)
                .build();
    }

    /**
     * 사용자의 마지막 invalidate 시각을 반환한다 (없거나 만료되면 null).
     *
     * 캐시 read 경로에서 TTL 판정과 함께 CAS 비교를 수행하기 위한 getter.
     * {@code invalidatedAt > cacheUpdatedAt}이면 row가 DELETE 되기 전 window 안에 있으므로
     * 호출 측은 stale로 간주해야 한다
     *
     * @param userId 조회 대상 사용자 ID
     * @return 마지막 invalidate 시각, 없으면 {@code null}
     */
    public Instant getLastInvalidatedAt(UUID userId) {
        return lastInvalidatedAt.getIfPresent(userId);
    }

    /**
     * 관심사 등록/삭제 직전의 시점(호출 측이 전달)을 받아,\ compute 시작 이후 invalidate가 발생했다면 쓰기를 스킵한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cache(UUID userId,
                      Instant computeStartedAt,
                      String summary,
                      String insightCardsJson,
                      String relatedKeywordsJson,
                      String sourceClusterIdsJson,
                      int sourceArticleCount,
                      PersonalizationMode mode) {
        Instant invalidatedAt = lastInvalidatedAt.getIfPresent(userId);
        if (invalidatedAt != null && invalidatedAt.isAfter(computeStartedAt)) {
            log.info("[UserMarketTrendCache] compute 이후 invalidate 발생 — 캐시 쓰기 스킵 (userId={}, started={}, invalidated={})",
                    userId, computeStartedAt, invalidatedAt);
            return;
        }
        LocalDate today = LocalDate.now(TREND_ZONE);
        userMarketTrendRepository.upsert(userId, today, mode.name(), summary,
                insightCardsJson, relatedKeywordsJson, sourceClusterIdsJson,
                sourceArticleCount);
        log.info("[UserMarketTrendCache] 캐시 저장 — userId={}, trendDate={}, mode={}, articles={}",
                userId, today, mode, sourceArticleCount);
    }

    /**
     * 사용자의 오늘자 캐시를 무효화한다 (관심사 추가/삭제 시 호출).
     *
     * 호출자가 트랜잭션 안에 있으면 afterCommit에서 실제 삭제를 수행하여:
     *
     *   관심사 저장이 롤백될 경우 캐시 삭제도 일어나지 않음 (일관성 유지)
     *   캐시 삭제 실패가 관심사 저장 트랜잭션을 말아먹지 않음 (best-effort)
     *   self-invocation 문제 없이 Spring Data의 기본 @Transactional이 적용됨
     *
     * 타임스탬프({@code lastInvalidatedAt})는 트랜잭션 활성 시 afterCommit에서 기록한다.
     * 커밋 전에 먼저 기록하면, 아직 커밋되지 않은 관심사 변경을 다른 경로의 compute가 구 데이터로 읽은 뒤
     * {@code invalidatedAt < computeStartedAt}으로 판정되어 stale 스냅샷을 캐시에 저장할 수 있기 때문이다.
     * 비트랜잭션 경로에서는 즉시 기록한다.
     */
    public void invalidateToday(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lastInvalidatedAt.put(userId, Instant.now());
                    deleteQuietly(userId);
                }
            });
        } else {
            lastInvalidatedAt.put(userId, Instant.now());
            deleteQuietly(userId);
        }
    }

    private void deleteQuietly(UUID userId) {
        LocalDate today = LocalDate.now(TREND_ZONE);
        try {
            userMarketTrendRepository.deleteByUserIdAndTrendDate(userId, today);
            log.info("[UserMarketTrendCache] 캐시 무효화 — userId={}, trendDate={}", userId, today);
        } catch (Exception e) {
            log.warn("[UserMarketTrendCache] 캐시 무효화 실패 — 본 요청은 정상 진행 (userId={})", userId, e);
        }
    }
}
