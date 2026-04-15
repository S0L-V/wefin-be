package com.solv.wefin.domain.market.trend.service;

import com.solv.wefin.domain.market.trend.dto.PersonalizationMode;
import com.solv.wefin.domain.market.trend.repository.UserMarketTrendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 사용자별 맞춤 동향 캐시 쓰기/무효화 전담 (트랜잭션 경계)
 *
 * 캐시 read는 {@link PersonalizedMarketTrendService} 내부에서 직접 수행하고,
 * 쓰기/삭제는 본 서비스를 통해 짧은 트랜잭션으로 처리한다 (AI 외부 호출과 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMarketTrendCacheService {

    public static final ZoneId TREND_ZONE = ZoneId.of("Asia/Seoul"); //캐시 TTL 기준 timezone

    private final UserMarketTrendRepository userMarketTrendRepository;
    private final ConcurrentMap<UUID, Instant> lastInvalidatedAt = new ConcurrentHashMap<>(); // 사용자별 마지막 invalidate 시각

    /**
     * 관심사 등록/삭제 직전의 시점(호출 측이 전달)을 받아, compute 시작 이후 invalidate가 발생했다면 쓰기를 스킵한다.
     * 쓰기 자체는 REQUIRES_NEW 트랜잭션으로 격리
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
        Instant invalidatedAt = lastInvalidatedAt.get(userId);
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
     * 사용자의 오늘자 캐시를 삭제한다 (관심사 추가/삭제 시 호출)
     */
    public void invalidateToday(UUID userId) {
        lastInvalidatedAt.put(userId, Instant.now());
        try {
            invalidateTodayInternal(userId);
        } catch (Exception e) {
            log.warn("[UserMarketTrendCache] 캐시 무효화 실패 — 관심사 등록은 정상 진행 (userId={})", userId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void invalidateTodayInternal(UUID userId) {
        LocalDate today = LocalDate.now(TREND_ZONE);
        userMarketTrendRepository.deleteByUserIdAndTrendDate(userId, today);
        log.info("[UserMarketTrendCache] 캐시 무효화 — userId={}, trendDate={}", userId, today);
    }
}
