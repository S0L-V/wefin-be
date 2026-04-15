package com.solv.wefin.domain.market.trend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.ClusterSummary;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.PersonalizedTrendRawResult;
import com.solv.wefin.domain.market.trend.dto.InsightCard;
import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.dto.PersonalizationMode;
import com.solv.wefin.domain.market.trend.dto.SourceClusterInfo;
import com.solv.wefin.domain.market.trend.entity.UserMarketTrend;
import com.solv.wefin.domain.market.trend.repository.UserMarketTrendRepository;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.service.ClusterTagAggregator;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 사용자 관심사 기반 맞춤 금융 동향 생성 서비스 (lazy: 호출 시점 기준 캐시 hit 또는 OpenAI 호출).
 *
 * 응답의 {@link PersonalizationMode}로 생성 방식을 명시한다.
 *   {@code MATCHED} — 관심사와 매칭된 클러스터 기반 맞춤 분석
 *   {@code ACTION_BRIEFING} — 매칭 0건이라 일반 24h 클러스터로 시장 액션 브리핑
 *   {@code OVERVIEW_FALLBACK} — 관심사 0개·클러스터 0건·AI 실패·검증 실패 등으로 overview 콘텐츠 그대로
 *
 * 캐시: {@code user_market_trend} 테이블에 (user_id, trend_date) unique. TTL 30분.
 * 관심사 변경 시 {@link UserMarketTrendCacheService#invalidateToday(UUID)}로 즉시 무효화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedMarketTrendService {

    private static final int MAX_CLUSTERS = OpenAiMarketTrendClient.MAX_CLUSTERS_IN_PROMPT; // 프롬프트에 포함할 클러스터 최대 개수 (overview와 동일)
    private static final int MAX_TAGS = OpenAiMarketTrendClient.MAX_TAGS_IN_PROMPT; // 떠오르는 종목/주제 태그 상한
    private static final int REQUIRED_CARDS = OpenAiMarketTrendClient.REQUIRED_INSIGHT_CARDS; // 카드 개수 제약
    private static final int MIN_KEYWORDS = 5;
    private static final int MAX_KEYWORDS = 10;
    private static final Duration LOOKBACK = Duration.ofHours(24); // 후보 클러스터의 최대 경과 시간
    private static final List<SummaryStatus> VISIBLE_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);
    private static final List<String> EMPTY_SENTINEL = List.of(""); // JPQL IN 빈 컬렉션 회피용 sentinel
    private static final Duration CACHE_TTL = Duration.ofMinutes(30); // 사용자 캐시 TTL — overview 배치(30분) 주기와 정렬


    private final MarketSnapshotRepository marketSnapshotRepository;
    private final UserInterestRepository userInterestRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleTagRepository newsArticleTagRepository;
    private final ClusterTagAggregator tagAggregator;
    private final OpenAiMarketTrendClient openAiClient;
    private final MarketTrendQueryService marketTrendQueryService;
    private final MarketTrendCardMapper cardMapper;
    private final UserMarketTrendRepository userMarketTrendRepository;
    private final UserMarketTrendCacheService userMarketTrendCacheService;
    private final ObjectMapper objectMapper;


    /**
     * JSON을 List<InsightCard>로 역직렬화하기 위한 타입 정보 객체
     */
    private static final TypeReference<List<InsightCard>> CARDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> KEYWORDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> CLUSTER_IDS_TYPE = new TypeReference<>() {
    };

    /**
     * TTL(30분) 내 캐시 row만 반환하고 AI 호출은 수행하지 않는다.
     *
     * 페이지 진입 시 "이전 분석 결과가 fresh면 자동 노출, stale/미생성이면 버튼 클릭 유도" 흐름에서 사용한다.
     * 캐시 miss/stale은 모두 {@link Optional#empty()}로 반환되어 호출 측이 204 등으로 처리할 수 있다
     *
     * @param userId 조회 대상 사용자 ID
     * @return fresh 캐시가 있으면 화면 DTO, 아니면 empty
     */
    public Optional<MarketTrendOverview> getCachedForUser(UUID userId) {
        return readFreshCache(userId).map(this::assembleFromCache);
    }

    /**
     * TTL + invalidate CAS를 동시에 검사해 재사용 가능한 캐시 row를 반환한다.
     *
     * TTL 안이더라도 invalidate가 row의 updatedAt 이후에 발생했다면 afterCommit DELETE 이전의
     * short window에서 stale row가 노출될 수 있으므로 miss로 취급한다.
     * {@link #getCachedForUser(UUID)} 와 {@link #getForUser(UUID)} 가 공유하는 단일 게이트
     */
    private Optional<UserMarketTrend> readFreshCache(UUID userId) {
        Optional<UserMarketTrend> cached = userMarketTrendRepository
                .findByUserIdAndTrendDate(userId, LocalDate.now(UserMarketTrendCacheService.TREND_ZONE));
        if (cached.isEmpty()) return Optional.empty();

        OffsetDateTime cacheUpdatedAt = cached.get().getUpdatedAt();
        boolean fresh = cacheUpdatedAt != null
                && cacheUpdatedAt.isAfter(OffsetDateTime.now().minus(CACHE_TTL));
        if (!fresh) {
            log.info("[PersonalizedMarketTrend] 캐시 stale (TTL 경과) — userId={}, cacheUpdatedAt={}",
                    userId, cacheUpdatedAt);
            return Optional.empty();
        }

        Instant invalidatedAt = userMarketTrendCacheService.getLastInvalidatedAt(userId);
        if (invalidatedAt != null && invalidatedAt.isAfter(cacheUpdatedAt.toInstant())) {
            log.info("[PersonalizedMarketTrend] 캐시 stale (invalidate window) — userId={}, cacheUpdatedAt={}, invalidatedAt={}",
                    userId, cacheUpdatedAt, invalidatedAt);
            return Optional.empty();
        }
        return cached;
    }

    public MarketTrendOverview getForUser(UUID userId) {
        // 0) 캐시 lookup — TTL(30분) 안 + invalidate 이후가 아니면 AI 호출 없이 즉시 반환
        Optional<UserMarketTrend> cached = readFreshCache(userId);
        if (cached.isPresent()) {
            log.info("[PersonalizedMarketTrend] 캐시 hit (fresh) — userId={}", userId);
            return assembleFromCache(cached.get());
        }

        // AI 호출 시작 시점 기록 — 캐시 저장 직전 invalidate가 있었는지 비교하여 stale 스냅샷 저장 방지
        Instant computeStartedAt = Instant.now();

        // 1) 관심사 직접 조회
        List<String> stockCodes = loadInterestCodes(userId, InterestType.STOCK);
        List<String> sectorCodes = loadInterestCodes(userId, InterestType.SECTOR);
        List<String> topicCodes = loadInterestCodes(userId, InterestType.TOPIC);

        if (stockCodes.isEmpty() && sectorCodes.isEmpty() && topicCodes.isEmpty()) {
            log.info("[PersonalizedMarketTrend] 관심사 없음 — overview 폴백 (userId={})", userId);
            return overviewFallback();
        }

        // 2) 매칭 클러스터 조회
        OffsetDateTime cutoff = OffsetDateTime.now().minus(LOOKBACK);
        List<NewsCluster> clusters = newsClusterRepository.findPersonalizedClusters(
                ClusterStatus.ACTIVE,
                VISIBLE_STATUSES,
                cutoff,
                NewsArticleTag.TagType.STOCK, orSentinel(stockCodes),
                NewsArticleTag.TagType.SECTOR, orSentinel(sectorCodes),
                NewsArticleTag.TagType.TOPIC, orSentinel(topicCodes),
                PageRequest.of(0, MAX_CLUSTERS));
        log.info("[PersonalizedMarketTrend] 매칭 클러스터 조회 — userId={}, stocks={}, sectors={}, topics={}, matched={}",
                userId, stockCodes, sectorCodes, topicCodes, clusters.size());

        boolean matched = !clusters.isEmpty();
        if (!matched) {
            clusters = newsClusterRepository.findRecentActiveClusters(
                    ClusterStatus.ACTIVE,
                    VISIBLE_STATUSES,
                    cutoff,
                    PageRequest.of(0, MAX_CLUSTERS));
            log.info("[PersonalizedMarketTrend] 직접 매칭 없음 — 시장 액션 브리핑으로 전환, 일반 클러스터 {}건 (userId={})",
                    clusters.size(), userId);
            if (clusters.isEmpty()) {
                log.info("[PersonalizedMarketTrend] 일반 클러스터도 없음 — overview 폴백 (userId={})", userId);
                return overviewFallback();
            }
        }

        // 3) 태그 집계
        List<Long> clusterIds = clusters.stream().map(NewsCluster::getId).toList();
        Map<Long, List<Long>> clusterArticleMap = clusterArticleRepository.findByNewsClusterIdIn(clusterIds)
                .stream()
                .collect(Collectors.groupingBy(
                        NewsClusterArticle::getNewsClusterId,
                        Collectors.mapping(NewsClusterArticle::getNewsArticleId, Collectors.toList())));
        List<Long> allArticleIds = clusterArticleMap.values().stream()
                .flatMap(List::stream).distinct().toList();

        List<String> risingStocks = cardMapper.collectTopStockNames(
                allArticleIds.isEmpty() ? Map.of() : tagAggregator.aggregateStocks(clusterArticleMap, allArticleIds),
                MAX_TAGS);
        List<String> risingTopics = cardMapper.collectTopTopicNames(
                allArticleIds.isEmpty() ? Map.of() : tagAggregator.aggregateMarketTags(clusterArticleMap, allArticleIds),
                MAX_TAGS);

        // 4) 관심사 표시명 (matched 모드에서만 프롬프트에 사용)
        List<String> stockNames = matched ? resolveTagNames(NewsArticleTag.TagType.STOCK, stockCodes) : List.of();
        List<String> sectorNames = matched ? resolveTagNames(NewsArticleTag.TagType.SECTOR, sectorCodes) : List.of();
        List<String> topicNames = matched ? resolveTagNames(NewsArticleTag.TagType.TOPIC, topicCodes) : List.of();

        // 5) 프롬프트용 클러스터 경량 DTO
        List<ClusterSummary> clusterSummaries = clusters.stream()
                .map(c -> new ClusterSummary(c.getId(), c.getTitle(), c.getSummary()))
                .toList();

        // 6) AI 호출 분기
        PersonalizedTrendRawResult raw;
        try {
            raw = matched
                    ? openAiClient.generatePersonalizedTrend(
                            marketSnapshotRepository.findAll(),
                            clusterSummaries,
                            risingStocks,
                            risingTopics,
                            stockNames,
                            sectorNames,
                            topicNames)
                    : openAiClient.generateMarketActionBriefing(
                            marketSnapshotRepository.findAll(),
                            clusterSummaries,
                            risingStocks,
                            risingTopics);
        } catch (OpenAiMarketTrendClient.MarketTrendAiException e) {
            log.warn("[PersonalizedMarketTrend] AI 호출 실패 — overview 폴백 (userId={}, mode={})",
                    userId, matched ? "matched" : "action", e);
            return overviewFallback();
        }

        if (raw.isEmpty()) {
            log.warn("[PersonalizedMarketTrend] summary 누락 — overview 폴백 (userId={})", userId);
            return overviewFallback();
        }

        // 7) 카드 매핑 + 검증
        List<InsightCard> cards = cardMapper.mapPersonalizedCards(raw.cards(), clusterSummaries);
        if (cards.size() != REQUIRED_CARDS) {
            log.warn("[PersonalizedMarketTrend] 카드 수 불일치 — overview 폴백 (expected={}, actual={})",
                    REQUIRED_CARDS, cards.size());
            return overviewFallback();
        }
        boolean hasEmptySource = cards.stream().anyMatch(c -> c.relatedClusterIds().isEmpty());
        if (hasEmptySource) {
            log.warn("[PersonalizedMarketTrend] 일부 카드에 출처 클러스터가 없음 — overview 폴백");
            return overviewFallback();
        }
        boolean missingAdvice = cards.stream()
                .anyMatch(c -> c.advice() == null || c.advice().isBlank()
                        || c.adviceLabel() == null || c.adviceLabel().isBlank());
        if (missingAdvice) {
            log.warn("[PersonalizedMarketTrend] 일부 카드에 advice/adviceLabel 누락 — overview 폴백");
            return overviewFallback();
        }
        int keywordCount = raw.relatedKeywords().size();
        if (keywordCount < MIN_KEYWORDS || keywordCount > MAX_KEYWORDS) {
            log.warn("[PersonalizedMarketTrend] 키워드 개수 범위 벗어남 — overview 폴백 (range={}~{}, actual={})",
                    MIN_KEYWORDS, MAX_KEYWORDS, keywordCount);
            return overviewFallback();
        }

        // 8) 응답 조립
        List<Long> sourceClusterIds = cardMapper.collectReferencedClusterIds(cards);
        List<SourceClusterInfo> sourceClusters = marketTrendQueryService.resolveSourceClusters(sourceClusterIds);
        List<MarketSnapshot> snapshots = marketSnapshotRepository.findAll();
        int sourceArticleCount = allArticleIds.size();
        PersonalizationMode resultMode = matched ? PersonalizationMode.MATCHED : PersonalizationMode.ACTION_BRIEFING;

        // 9) 캐시 저장 (matched/action briefing 모두). 직렬화 실패는 응답 영향 없게 처리
        try {
            userMarketTrendCacheService.cache(
                    userId,
                    computeStartedAt,
                    raw.summary(),
                    objectMapper.writeValueAsString(cards),
                    objectMapper.writeValueAsString(raw.relatedKeywords()),
                    objectMapper.writeValueAsString(sourceClusterIds),
                    sourceArticleCount,
                    resultMode);
        } catch (JsonProcessingException e) {
            log.warn("[PersonalizedMarketTrend] 캐시 직렬화 실패 — 응답은 정상 반환 (userId={})", userId, e);
        }

        return new MarketTrendOverview(
                true,
                resultMode,
                LocalDate.now(UserMarketTrendCacheService.TREND_ZONE),
                null,
                raw.summary(),
                cards,
                raw.relatedKeywords(),
                sourceClusters,
                sourceArticleCount,
                OffsetDateTime.now(),
                snapshots
        );
    }

    private List<String> loadInterestCodes(UUID userId, InterestType type) {
        return userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, type.name())
                .stream().map(UserInterest::getInterestValue).toList();
    }

    private List<String> resolveTagNames(NewsArticleTag.TagType type, List<String> codes) {
        if (codes.isEmpty()) return List.of();
        Map<String, String> nameByCode = newsArticleTagRepository
                .findTagNamesByTagTypeAndTagCodes(type.name(), codes)
                .stream()
                .collect(Collectors.toMap(
                        NewsArticleTagRepository.TagNameProjection::getCode,
                        NewsArticleTagRepository.TagNameProjection::getName));
        return codes.stream().map(c -> nameByCode.getOrDefault(c, c)).toList();
    }

    /** 캐시 row를 응답 DTO로 풀어낸다. JSON 파싱 실패 시 빈 컬렉션으로 fallback */
    private MarketTrendOverview assembleFromCache(UserMarketTrend cached) {
        List<InsightCard> rawCards = parseList(cached.getInsightCardsJson(), CARDS_TYPE);
        List<String> keywords = parseList(cached.getRelatedKeywordsJson(), KEYWORDS_TYPE);
        List<Long> sourceClusterIds = parseList(cached.getSourceClusterIdsJson(), CLUSTER_IDS_TYPE);
        List<SourceClusterInfo> sourceClusters = marketTrendQueryService.resolveSourceClusters(sourceClusterIds);
        List<MarketSnapshot> snapshots = marketSnapshotRepository.findAll();
        PersonalizationMode mode = cached.getMode() != null ? cached.getMode() : PersonalizationMode.MATCHED;

        // 카드의 relatedClusterIds에서 현재 active(resolved)가 아닌 클러스터는 제외.
        java.util.Set<Long> activeIds = sourceClusters.stream()
                .map(SourceClusterInfo::clusterId)
                .collect(java.util.stream.Collectors.toSet());
        List<InsightCard> cards = rawCards.stream()
                .map(c -> {
                    List<Long> filtered = c.relatedClusterIds().stream()
                            .filter(activeIds::contains)
                            .toList();
                    return new InsightCard(c.headline(), c.body(), c.advice(), c.adviceLabel(), filtered);
                })
                .toList();

        return new MarketTrendOverview(
                true,
                mode,
                cached.getTrendDate(),
                null,
                cached.getSummary(),
                cards,
                keywords,
                sourceClusters,
                cached.getSourceArticleCount(),
                cached.getUpdatedAt(),
                snapshots
        );
    }

    /**
     * 모든 폴백 경로에서 재사용하는 helper
     *
     * overview 응답(mode=null)을 PERSONALIZED 관점의 OVERVIEW_FALLBACK으로 표시해 프론트가
     * "맞춤 분석이 불가하여 일반 시황으로 제공됨"으로 분기할 수 있게 한다
     */
    private MarketTrendOverview overviewFallback() {
        return marketTrendQueryService.getOverview().withMode(PersonalizationMode.OVERVIEW_FALLBACK);
    }

    /**
     * 빈 코드 리스트를 JPQL {@code IN ()} 에러 없이 OR 조건에 넣기 위한 sentinel wrapper.
     * tag_code에 빈 문자열 row가 저장되지 않는다는 가정에 기반
     */
    private static List<String> orSentinel(List<String> codes) {
        return codes.isEmpty() ? EMPTY_SENTINEL : codes;
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<T> result = objectMapper.readValue(json, type);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("[PersonalizedMarketTrend] 캐시 JSON 파싱 실패 — 빈 리스트 fallback: {}", e.getMessage());
            return List.of();
        }
    }
}
