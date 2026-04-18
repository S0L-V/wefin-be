package com.solv.wefin.domain.news.recommendation.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationAiException;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationCardResult;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.CandidateInterest;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.CardState;
import com.solv.wefin.domain.news.recommendation.service.RecommendationTxService.GenerationPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 추천 뉴스 카드 오케스트레이터
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRecommendationService {

    private static final List<SummaryStatus> VISIBLE_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);

    private final RecommendationTxService txService;
    private final OpenAiRecommendationClient aiClient;
    private final RecommendationReasonBuilder reasonBuilder;
    private final NewsClusterRepository newsClusterRepository;

    /**
     * 추천 뉴스 카드를 조회한다
     */
    public RecommendationResult getRecommendedCards(UUID userId) {
        CardState state = txService.resolveGetState(userId);
        return processState(state);
    }

    /**
     * 추천 뉴스 카드를 교체한다 (다른 뉴스 불러오기)
     */
    public RecommendationResult refreshCards(UUID userId) {
        CardState state = txService.resolveRefreshState(userId);
        return processState(state);
    }

    private RecommendationResult processState(CardState state) {
        RecommendationResult result;
        if (state instanceof CardState.Cached cached) {
            result = cached.result();
        } else if (state instanceof CardState.NeedsGeneration needs) {
            result = generateAndSave(needs.plan());
        } else {
            throw new IllegalStateException("Unknown CardState: " + state.getClass());
        }
        Map<Long, NewsCluster> clusterMap = resolveLinkedClusters(result);
        return new RecommendationResult(result.cards(), result.hasMore(),
                result.refreshCount(), result.refreshLimit(), clusterMap);
    }

    /**
     * AI 호출(TX 밖) → 저장(짧은 TX)
     *
     * 후보가 있었지만 새 카드를 0건 생성한 경우 hasMore를 false로 강제한다.
     * 남은 관심사가 모두 클러스터 미매칭이거나 AI 실패인 상황에서
     * 사용자가 "더 불러오기"를 반복하는 것을 방지한다
     */
    private RecommendationResult generateAndSave(GenerationPlan plan) {
        List<RecommendedNewsCard> newCards = new ArrayList<>();
        boolean hadCandidates = !plan.stockCandidates().isEmpty() || !plan.sectorCandidates().isEmpty();

        if (!plan.stockCandidates().isEmpty()) {
            RecommendedNewsCard card = generateCardFromCandidates(
                    plan.userId(), plan.stockCandidates(), plan.allInterestNames(),
                    plan.interestHash(), plan.sessionStartedAt());
            if (card != null) newCards.add(card);
        }

        if (!plan.sectorCandidates().isEmpty()) {
            RecommendedNewsCard card = generateCardFromCandidates(
                    plan.userId(), plan.sectorCandidates(), plan.allInterestNames(),
                    plan.interestHash(), plan.sessionStartedAt());
            if (card != null) newCards.add(card);
        }

        boolean generationFailed = hadCandidates && newCards.isEmpty();

        RecommendationResult result = txService.saveAndBuildResult(
                plan.userId(), plan.interestHash(), newCards);

        if (generationFailed) {
            return new RecommendationResult(result.cards(), false,
                    result.refreshCount(), result.refreshLimit(), result.linkedClusterMap());
        }
        return result;
    }

    /**
     * 후보 목록 중 첫 번째 성공하는 관심사로 카드를 생성한다
     */
    private RecommendedNewsCard generateCardFromCandidates(UUID userId,
                                                            List<CandidateInterest> candidates,
                                                            List<String> allInterestNames,
                                                            String interestHash,
                                                            java.time.OffsetDateTime sessionStartedAt) {
        for (CandidateInterest candidate : candidates) {
            try {
                RecommendationCardResult aiResult = aiClient.generate(
                        candidate.cardType(), candidate.code(), candidate.name(),
                        candidate.clusters(), allInterestNames);

                int linkedIndex = aiResult.linkedClusterIndex();
                Long linkedClusterId = candidate.clusters().get(linkedIndex).clusterId();

                String reasons = reasonBuilder.buildReasonsJson(
                        userId, candidate.cardType(), candidate.code(), candidate.name());

                log.info("[RecommendedNews] GENERATED — userId={}, cardType={}, interestCode={}, clusterCount={}",
                        userId, candidate.cardType(), candidate.code(), candidate.clusters().size());

                return RecommendedNewsCard.builder()
                        .userId(userId)
                        .cardType(candidate.cardType())
                        .interestCode(candidate.code())
                        .interestName(candidate.name())
                        .title(aiResult.title())
                        .summary(aiResult.summary())
                        .context(aiResult.context())
                        .reasons(reasons)
                        .linkedClusterId(linkedClusterId)
                        .interestHash(interestHash)
                        .sessionStartedAt(sessionStartedAt)
                        .build();
            } catch (RecommendationAiException e) {
                log.error("[RecommendedNews] AI_FAILED — userId={}, cardType={}, interestCode={}: {}",
                        userId, candidate.cardType(), candidate.code(), e.getMessage(), e);
            }
        }

        log.debug("[RecommendedNews] 모든 후보 AI 생성 실패 — userId={}", userId);
        return null;
    }

    /**
     * 결과 카드에 연결된 클러스터 정보를 조회하여 매핑을 반환한다.
     * INACTIVE/삭제된 클러스터는 매핑에 포함되지 않는다
     */
    private Map<Long, NewsCluster> resolveLinkedClusters(RecommendationResult result) {
        List<Long> clusterIds = result.cards().stream()
                .map(RecommendedNewsCard::getLinkedClusterId)
                .distinct()
                .toList();

        if (clusterIds.isEmpty()) return Map.of();

        return newsClusterRepository
                .findByIdInAndStatusAndSummaryStatusIn(clusterIds, ClusterStatus.ACTIVE, VISIBLE_STATUSES)
                .stream()
                .collect(Collectors.toMap(NewsCluster::getId, Function.identity()));
    }

    /** 추천 결과 — 카드 목록 + 더 불러오기 가능 여부 + refresh 횟수 정보 + 연결 클러스터 매핑 */
    public record RecommendationResult(
            List<RecommendedNewsCard> cards,
            boolean hasMore,
            int refreshCount,
            int refreshLimit,
            Map<Long, NewsCluster> linkedClusterMap
    ) {
    }
}
