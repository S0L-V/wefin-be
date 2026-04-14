package com.solv.wefin.domain.market.trend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.ClusterSummary;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.MarketTrendRawResult;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.ParsedCard;
import com.solv.wefin.domain.market.trend.dto.InsightCard;
import com.solv.wefin.domain.market.trend.dto.MarketTrendContent;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.service.ClusterTagAggregator;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 오늘의 금융 동향 생성 배치 오케스트레이션
 *
 * 시장 지표 + 최근 주요 클러스터 + 태그 집계를 수집하여 AI 프롬프트를 조립하고,
 * AI 응답의 relatedClusterIndices(1-based)를 실제 clusterId로 매핑한 뒤
 * {@link MarketTrendPersistenceService}에 upsert를 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketTrendGenerationService {

    /** 프롬프트에 포함할 대표 클러스터 최대 개수 (AI 측과 동일 상수) */
    private static final int MAX_CLUSTERS = OpenAiMarketTrendClient.MAX_CLUSTERS_IN_PROMPT;
    private static final int MAX_TAGS = OpenAiMarketTrendClient.MAX_TAGS_IN_PROMPT;
    private static final int REQUIRED_CARDS = OpenAiMarketTrendClient.REQUIRED_INSIGHT_CARDS;
    private static final int MIN_KEYWORDS = 5;
    private static final int MAX_KEYWORDS = 10;
    /** 프롬프트 대상 클러스터의 최대 경과 시간 */
    private static final java.time.Duration LOOKBACK = java.time.Duration.ofHours(24);
    private static final List<SummaryStatus> VISIBLE_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);
    /** trend_date 계산 기준 timezone — 금융/뉴스 "오늘" 데이터 경계는 서비스 운영 지역(한국) 기준으로 고정 */
    private static final ZoneId TREND_ZONE = ZoneId.of("Asia/Seoul");

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final ClusterTagAggregator tagAggregator;
    private final OpenAiMarketTrendClient openAiClient;
    private final MarketTrendPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    /**
     * 오늘의 금융 동향을 생성하여 저장한다 (배치 entry point)
     */
    public void generateTodayTrend() {
        // 1) 시장 지표 4건
        List<MarketSnapshot> snapshots = marketSnapshotRepository.findAll();
        if (snapshots.isEmpty()) {
            log.warn("[MarketTrend] 시장 지표가 비어있음 — 생성 스킵");
            return;
        }

        // 2) 최근 LOOKBACK(기본 24h) 이내 주요 클러스터 상위 N건 (publishedAt 최신순)
        //    cutoff 조건은 DB 쿼리에서 직접 적용 — publishedAt이 null인 row가 상단에 오는 경우에도
        //    유효한 최근 클러스터가 limit 밖으로 밀리지 않도록 보장
        OffsetDateTime cutoff = OffsetDateTime.now().minus(LOOKBACK);
        List<NewsCluster> clusters = newsClusterRepository.findRecentActiveClusters(
                ClusterStatus.ACTIVE, VISIBLE_STATUSES, cutoff, PageRequest.of(0, MAX_CLUSTERS));
        if (clusters.isEmpty()) {
            log.warn("[MarketTrend] 최근 {}시간 내 가용 클러스터 없음 — 생성 스킵", LOOKBACK.toHours());
            return;
        }

        // 3) 태그 집계용 article 매핑 구성
        List<Long> clusterIds = clusters.stream().map(NewsCluster::getId).toList();
        Map<Long, List<Long>> clusterArticleMap = clusterArticleRepository.findByNewsClusterIdIn(clusterIds)
                .stream()
                .collect(Collectors.groupingBy(
                        NewsClusterArticle::getNewsClusterId,
                        Collectors.mapping(NewsClusterArticle::getNewsArticleId, Collectors.toList())));
        List<Long> allArticleIds = clusterArticleMap.values().stream()
                .flatMap(List::stream).distinct().toList();

        List<String> risingStocks = collectTopStockNames(
                allArticleIds.isEmpty() ? Map.of() : tagAggregator.aggregateStocks(clusterArticleMap, allArticleIds),
                MAX_TAGS);
        List<String> risingTopics = collectTopTopicNames(
                allArticleIds.isEmpty() ? Map.of() : tagAggregator.aggregateMarketTags(clusterArticleMap, allArticleIds),
                MAX_TAGS);

        // 4) 프롬프트에 넘길 클러스터 요약
        List<ClusterSummary> clusterSummaries = clusters.stream()
                .map(c -> new ClusterSummary(c.getId(), c.getTitle(), c.getSummary()))
                .toList();

        // 5) AI 호출
        MarketTrendRawResult raw;
        try {
            raw = openAiClient.generateTrend(snapshots, clusterSummaries, risingStocks, risingTopics);
        } catch (OpenAiMarketTrendClient.MarketTrendAiException e) {
            log.warn("[MarketTrend] AI 생성 실패 — 이번 실행 주기 스킵", e);
            return;
        }

        if (raw.isEmpty()) {
            log.warn("[MarketTrend] AI 결과에 title/summary 누락 — 저장 스킵");
            return;
        }

        // 6) relatedClusterIndices(1-based prompt index) → 실제 clusterId 매핑
        List<InsightCard> cards = mapCards(raw.cards(), clusterSummaries);

        // 7) 카드/키워드 개수 계약 검증 (프론트 레이아웃 보장)
        if (cards.size() != REQUIRED_CARDS) {
            log.warn("[MarketTrend] 인사이트 카드 개수 불일치 — 저장 스킵, expected: {}, actual: {}",
                    REQUIRED_CARDS, cards.size());
            return;
        }
        // 각 카드가 최소 1개 이상의 유효한 출처 클러스터를 가져야 함. AI가 범위 밖 index만
        // 반환한 경우 본문만 있고 관련 기사 링크가 비는 카드가 저장되는 것을 방지
        boolean hasEmptySource = cards.stream().anyMatch(c -> c.relatedClusterIds().isEmpty());
        if (hasEmptySource) {
            log.warn("[MarketTrend] 일부 인사이트 카드에 유효한 출처 클러스터가 없음 — 저장 스킵");
            return;
        }
        int keywordCount = raw.relatedKeywords().size();
        if (keywordCount < MIN_KEYWORDS || keywordCount > MAX_KEYWORDS) {
            log.warn("[MarketTrend] 키워드 개수 범위 벗어남 — 저장 스킵, range: {}~{}, actual: {}",
                    MIN_KEYWORDS, MAX_KEYWORDS, keywordCount);
            return;
        }

        // 8) 출처 메타 계산
        //    - sourceClusterIds: AI가 insightCards에서 실제로 참조한 클러스터만 (중복 제거 + 입력 순서 유지).
        //      프롬프트에 넘긴 15개 전부가 아니라 AI 동의한 것만 저장하여, 프론트의 "이 동향의 출처" 섹션이
        //      본문과 실제로 관련된 카드만 노출하도록 보장
        //    - sourceArticleCount: 전체 고유 기사 수. clusterArticleMap.values() 합산은 같은 기사가 복수
        //      클러스터에 속할 때 중복 카운트되므로 allArticleIds.size()로 정확도 확보
        java.util.LinkedHashSet<Long> referencedClusterIds = new java.util.LinkedHashSet<>();
        for (InsightCard card : cards) {
            referencedClusterIds.addAll(card.relatedClusterIds());
        }
        List<Long> sourceClusterIds = List.copyOf(referencedClusterIds);
        int sourceArticleCount = allArticleIds.size();

        // 9) 저장 (upsert)
        MarketTrendContent content = new MarketTrendContent(
                raw.title(), raw.summary(), cards, raw.relatedKeywords());
        try {
            persistenceService.upsertDailyTrend(
                    LocalDate.now(TREND_ZONE), content,
                    toJson(cards), toJson(raw.relatedKeywords()),
                    toJson(sourceClusterIds), sourceArticleCount);
        } catch (JsonProcessingException e) {
            log.warn("[MarketTrend] JSON 직렬화 실패 — 저장 스킵", e);
        }
    }

    /**
     * 클러스터별 STOCK 태그를 flatten하여 상위 name을 반환한다.
     */
    private List<String> collectTopStockNames(Map<Long, List<StockInfo>> perCluster, int topN) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> codeToName = new LinkedHashMap<>();
        perCluster.values().forEach(list -> {
            if (list == null) return;
            list.forEach(info -> {
                counts.merge(info.code(), 1, Integer::sum);
                codeToName.putIfAbsent(info.code(), info.name());
            });
        });
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(e -> codeToName.get(e.getKey()))
                .toList();
    }

    private List<String> collectTopTopicNames(Map<Long, List<String>> perCluster, int topN) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        perCluster.values().forEach(list -> {
            if (list == null) return;
            list.forEach(name -> counts.merge(name, 1, Integer::sum));
        });
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * AI가 반환한 relatedClusterIndices(1-based)를 실제 clusterId로 매핑한다.
     */
    private List<InsightCard> mapCards(List<ParsedCard> parsed, List<ClusterSummary> clusterSummaries) {
        List<InsightCard> result = new ArrayList<>();
        for (ParsedCard p : parsed) {
            if (p == null || !p.isValid()) continue;
            List<Long> ids = new ArrayList<>();
            for (Integer idx : p.relatedClusterIndices()) {
                if (idx == null) continue;
                if (idx < 1 || idx > clusterSummaries.size()) continue;
                ids.add(clusterSummaries.get(idx - 1).clusterId());
            }
            result.add(new InsightCard(p.headline(), p.body(), List.copyOf(ids)));
        }
        return List.copyOf(result);
    }

    private String toJson(Object value) throws JsonProcessingException {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
