package com.solv.wefin.domain.news.recommendation.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.ClusterInput;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import com.solv.wefin.domain.news.recommendation.repository.RecommendedNewsCardRepository;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 추천 뉴스 카드의 트랜잭션 경계 내 DB 작업을 담당한다
 *
 * advisory lock 획득, 세션 만료 정리, 캐시 판정, 후보 관심사 수집, 카드 저장을
 * 짧은 트랜잭션으로 분리하여 외부 API 호출이 트랜잭션을 점유하지 않도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationTxService {

    static final Duration SESSION_TTL = Duration.ofHours(6);
    static final Duration STALE_COOLDOWN = Duration.ofMinutes(30);
    static final Duration CLUSTER_LOOKBACK = Duration.ofHours(48);
    static final int MAX_CLUSTERS_PER_CARD = 5;
    static final int DAILY_REFRESH_LIMIT = 5;
    private static final List<String> EMPTY_SENTINEL = List.of("");
    private static final List<SummaryStatus> VISIBLE_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);

    @PersistenceContext
    private EntityManager entityManager;
    private final RecommendedNewsCardRepository cardRepository;
    private final UserInterestRepository userInterestRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final NewsArticleTagRepository newsArticleTagRepository;

    /**
     * 캐시 판정 + 생성 필요 시 후보 수집
     */
    @Transactional
    public CardState resolveGetState(UUID userId) {
        acquireAdvisoryLock(userId);
        cleanExpiredSession(userId);

        List<RecommendedNewsCard> existingCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String currentHash = computeInterestHash(userId);

        if (!existingCards.isEmpty()) {
            String storedHash = existingCards.get(0).getInterestHash();

            if (currentHash.equals(storedHash)) {
                return new CardState.Cached(buildResult(userId, existingCards));
            }

            OffsetDateTime latestCreatedAt = existingCards.get(0).getCreatedAt();
            boolean withinCooldown = latestCreatedAt.plus(STALE_COOLDOWN).isAfter(OffsetDateTime.now());
            if (withinCooldown) {
                log.debug("[RecommendedNews] hash 불일치 + 쿨다운 적용 — userId={}", userId);
                return new CardState.Cached(buildResult(userId, existingCards));
            }

            log.info("[RecommendedNews] hash 불일치 + 쿨다운 경과 → 이력 삭제 후 재생성 — userId={}", userId);
            cardRepository.deleteByUserId(userId);
        }

        return collectCandidates(userId, currentHash, List.of(), List.of());
    }

    /**
     * 캐시 판정 + 생성 필요 시 후보 수집
     *
     * hash 불일치 시 쿨다운 미적용으로 즉시 이력을 삭제한다.
     * 이력이 유효하면 이미 사용된 관심사를 제외하고 후보를 수집한다
     */
    @Transactional
    public CardState resolveRefreshState(UUID userId) {
        acquireAdvisoryLock(userId);
        cleanExpiredSession(userId);

        List<RecommendedNewsCard> existingCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String currentHash = computeInterestHash(userId);

        if (!existingCards.isEmpty()) {
            String storedHash = existingCards.get(0).getInterestHash();

            if (!currentHash.equals(storedHash)) {
                log.info("[RecommendedNews] refresh: hash 불일치 → 이력 삭제 후 재생성 — userId={}", userId);
                cardRepository.deleteByUserId(userId);
                return collectCandidates(userId, currentHash, List.of(), List.of());
            }

            checkRefreshLimit(userId);
        }

        List<String> usedStockCodes = cardRepository.findUsedInterestCodes(userId, CardType.STOCK);
        List<String> usedSectorCodes = cardRepository.findUsedInterestCodes(userId, CardType.SECTOR);

        return collectCandidates(userId, currentHash, usedStockCodes, usedSectorCodes);
    }

    /**
     * 세션 내 refresh 횟수가 일일 제한을 초과하는지 검사한다
     */
    private void checkRefreshLimit(UUID userId) {
        int stockUsed = cardRepository.findUsedInterestCodes(userId, CardType.STOCK).size();
        int sectorUsed = cardRepository.findUsedInterestCodes(userId, CardType.SECTOR).size();
        int refreshCount = Math.max(stockUsed, sectorUsed) - 1;

        if (refreshCount >= DAILY_REFRESH_LIMIT) {
            log.info("[RecommendedNews] refresh 제한 초과 — userId={}, refreshCount={}", userId, refreshCount);
            throw new BusinessException(ErrorCode.RECOMMENDATION_REFRESH_LIMIT_EXCEEDED);
        }
    }

    /**
     * Phase 3: AI 생성 결과를 저장한다
     *
     * advisory lock을 다시 잡고, 동일 interest_code 카드가 이미 존재하면 스킵한다.
     * hash 비교가 아닌 interest_code 기반 중복 확인으로, 정상 refresh와 동시 요청 방어를 모두 보장한다
     */
    @Transactional
    public NewsRecommendationService.RecommendationResult saveAndBuildResult(
            UUID userId, String interestHash, List<RecommendedNewsCard> newCards) {
        acquireAdvisoryLock(userId);

        List<RecommendedNewsCard> existingCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (!newCards.isEmpty()) {
            Set<String> existingKeys = existingCards.stream()
                    .map(c -> c.getCardType() + ":" + c.getInterestCode())
                    .collect(Collectors.toSet());
            List<RecommendedNewsCard> cardsToSave = newCards.stream()
                    .filter(c -> !existingKeys.contains(c.getCardType() + ":" + c.getInterestCode()))
                    .toList();
            if (!cardsToSave.isEmpty()) {
                cardRepository.saveAll(cardsToSave);
                log.info("[RecommendedNews] 카드 저장 — userId={}, count={}", userId, cardsToSave.size());
            } else {
                log.debug("[RecommendedNews] 동시 요청이 이미 동일 관심사 카드를 생성함 — userId={}", userId);
            }
        }

        List<RecommendedNewsCard> allCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return buildResult(userId, allCards);
    }

    /**
     * 후보 관심사를 수집하여 생성 계획을 반환한다.
     * 사용 가능한 관심사가 없으면 빈 결과를 Cached로 반환한다
     */
    private CardState collectCandidates(UUID userId, String interestHash,
                                         List<String> usedStockCodes,
                                         List<String> usedSectorCodes) {
        List<UserInterest> stockInterests = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "STOCK");
        List<UserInterest> sectorInterests = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "SECTOR");

        List<String> availableStocks = stockInterests.stream()
                .map(UserInterest::getInterestValue)
                .filter(code -> !usedStockCodes.contains(code))
                .toList();
        List<String> availableSectors = sectorInterests.stream()
                .map(UserInterest::getInterestValue)
                .filter(code -> !usedSectorCodes.contains(code))
                .toList();

        if (availableStocks.isEmpty() && availableSectors.isEmpty()) {
            log.debug("[RecommendedNews] 사용 가능한 관심사 없음 — userId={}, usedStock={}, usedSector={}",
                    userId, usedStockCodes.size(), usedSectorCodes.size());
            List<RecommendedNewsCard> existingCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return new CardState.Cached(buildResult(userId, existingCards));
        }

        OffsetDateTime sessionStartedAt = resolveSessionStartedAt(userId);
        List<String> allInterestNames = collectAllInterestNames(stockInterests, sectorInterests);

        List<CandidateInterest> stockCandidates = buildCandidates(CardType.STOCK, availableStocks);
        List<CandidateInterest> sectorCandidates = buildCandidates(CardType.SECTOR, availableSectors);

        if (stockCandidates.isEmpty() && sectorCandidates.isEmpty()) {
            log.debug("[RecommendedNews] 매칭 클러스터 있는 후보 없음 — userId={}", userId);
            List<RecommendedNewsCard> existingCards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return new CardState.Cached(buildResult(userId, existingCards));
        }

        return new CardState.NeedsGeneration(new GenerationPlan(
                userId, interestHash, sessionStartedAt,
                stockCandidates, sectorCandidates, allInterestNames));
    }

    /**
     * 사용 가능한 관심사 코드 목록에서 매칭 클러스터가 있는 후보를 랜덤 순서로 수집한다.
     * 클러스터가 없는 관심사는 스킵하고 로그를 남긴다
     */
    private List<CandidateInterest> buildCandidates(CardType cardType, List<String> availableCodes) {
        List<String> shuffled = new ArrayList<>(availableCodes);
        Collections.shuffle(shuffled);

        List<CandidateInterest> candidates = new ArrayList<>();
        for (String code : shuffled) {
            List<NewsCluster> clusters = findClustersForInterest(cardType, code);
            if (clusters.isEmpty()) {
                log.warn("[RecommendedNews] NO_MATCHING_CLUSTER — cardType={}, interestCode={}", cardType, code);
                continue;
            }

            String interestName = resolveInterestName(cardType, code);
            List<ClusterInput> clusterInputs = clusters.stream()
                    .limit(MAX_CLUSTERS_PER_CARD)
                    .map(c -> new ClusterInput(c.getId(), c.getTitle(),
                            c.getSummary() != null ? c.getSummary() : ""))
                    .toList();

            candidates.add(new CandidateInterest(cardType, code, interestName, clusterInputs));
        }
        return candidates;
    }

    private List<NewsCluster> findClustersForInterest(CardType cardType, String interestCode) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(CLUSTER_LOOKBACK);

        List<String> stockCodes = cardType == CardType.STOCK ? List.of(interestCode) : EMPTY_SENTINEL;
        List<String> sectorCodes = cardType == CardType.SECTOR ? List.of(interestCode) : EMPTY_SENTINEL;

        return newsClusterRepository.findPersonalizedClusters(
                ClusterStatus.ACTIVE, VISIBLE_STATUSES, cutoff,
                NewsArticleTag.TagType.STOCK, stockCodes,
                NewsArticleTag.TagType.SECTOR, sectorCodes,
                NewsArticleTag.TagType.TOPIC, EMPTY_SENTINEL,
                PageRequest.of(0, MAX_CLUSTERS_PER_CARD));
    }

    private String resolveInterestName(CardType cardType, String code) {
        return newsArticleTagRepository
                .findTagNamesByTagTypeAndTagCodes(cardType.name(), List.of(code))
                .stream()
                .findFirst()
                .map(NewsArticleTagRepository.TagNameProjection::getName)
                .orElse(code);
    }

    private List<String> collectAllInterestNames(List<UserInterest> stockInterests,
                                                  List<UserInterest> sectorInterests) {
        List<String> names = new ArrayList<>();
        resolveNames("STOCK", stockInterests, names);
        resolveNames("SECTOR", sectorInterests, names);
        return names;
    }

    private void resolveNames(String type, List<UserInterest> interests, List<String> out) {
        List<String> codes = interests.stream().map(UserInterest::getInterestValue).toList();
        if (codes.isEmpty()) return;
        newsArticleTagRepository.findTagNamesByTagTypeAndTagCodes(type, codes)
                .forEach(p -> out.add(p.getName()));
    }

    private OffsetDateTime resolveSessionStartedAt(UUID userId) {
        return cardRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .findFirst()
                .map(RecommendedNewsCard::getSessionStartedAt)
                .orElse(OffsetDateTime.now());
    }

    private void cleanExpiredSession(UUID userId) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(SESSION_TTL);
        int deleted = cardRepository.deleteExpiredCards(userId, cutoff);
        if (deleted > 0) {
            log.info("[RecommendedNews] 세션 만료 카드 삭제 — userId={}, count={}", userId, deleted);
        }
    }

    private String computeInterestHash(UUID userId) {
        List<String> stockCodes = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "STOCK")
                .stream().map(UserInterest::getInterestValue).sorted().toList();
        List<String> sectorCodes = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, "SECTOR")
                .stream().map(UserInterest::getInterestValue).sorted().toList();

        String input = "STOCK:" + String.join(",", stockCodes) +
                "|SECTOR:" + String.join(",", sectorCodes);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    private void acquireAdvisoryLock(UUID userId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .setParameter(1, "recommended_news:" + userId)
                .getSingleResult();
    }

    /**
     * 카드 목록에서 타입별 최신 카드를 선택하여 결과를 구성한다
     */
    NewsRecommendationService.RecommendationResult buildResult(
            UUID userId, List<RecommendedNewsCard> allCards) {
        Optional<RecommendedNewsCard> stockCard = allCards.stream()
                .filter(c -> c.getCardType() == CardType.STOCK)
                .findFirst();
        Optional<RecommendedNewsCard> sectorCard = allCards.stream()
                .filter(c -> c.getCardType() == CardType.SECTOR)
                .findFirst();

        List<RecommendedNewsCard> latestCards = new ArrayList<>();
        stockCard.ifPresent(latestCards::add);
        sectorCard.ifPresent(latestCards::add);

        boolean hasMoreStock = hasMoreInterests(userId, CardType.STOCK);
        boolean hasMoreSector = hasMoreInterests(userId, CardType.SECTOR);

        int stockUsed = cardRepository.findUsedInterestCodes(userId, CardType.STOCK).size();
        int sectorUsed = cardRepository.findUsedInterestCodes(userId, CardType.SECTOR).size();
        int refreshCount = Math.max(0, Math.max(stockUsed, sectorUsed) - 1);

        return new NewsRecommendationService.RecommendationResult(
                latestCards, hasMoreStock || hasMoreSector,
                refreshCount, DAILY_REFRESH_LIMIT, Map.of());
    }

    private boolean hasMoreInterests(UUID userId, CardType cardType) {
        List<String> usedCodes = cardRepository.findUsedInterestCodes(userId, cardType);
        List<UserInterest> allInterests = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, cardType.name());

        return allInterests.stream()
                .anyMatch(i -> !usedCodes.contains(i.getInterestValue()));
    }

    // ── Phase 1 반환 타입 ──────────────────────────────────

    /** Phase 1 결과: 캐시 hit이거나 생성이 필요한 상태 */
    public sealed interface CardState {
        record Cached(NewsRecommendationService.RecommendationResult result) implements CardState {}
        record NeedsGeneration(GenerationPlan plan) implements CardState {}
    }

    /** 생성 필요 시 수집된 후보 정보 — AI 호출에 필요한 모든 데이터를 포함한다 */
    public record GenerationPlan(
            UUID userId,
            String interestHash,
            OffsetDateTime sessionStartedAt,
            List<CandidateInterest> stockCandidates,
            List<CandidateInterest> sectorCandidates,
            List<String> allInterestNames
    ) {}

    /** 클러스터가 매칭된 단일 관심사 후보 */
    public record CandidateInterest(
            CardType cardType,
            String code,
            String name,
            List<ClusterInput> clusters
    ) {}
}
