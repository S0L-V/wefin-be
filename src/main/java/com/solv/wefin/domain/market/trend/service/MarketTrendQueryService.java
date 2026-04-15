package com.solv.wefin.domain.market.trend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import com.solv.wefin.domain.market.trend.dto.InsightCard;
import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.dto.SourceClusterInfo;
import com.solv.wefin.domain.market.trend.entity.MarketTrend;
import com.solv.wefin.domain.market.trend.repository.MarketTrendRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 금융 동향 조회 서비스
 *
 * 최신 {@link MarketTrend} 1건과 현재 시장 지표 4건을 조합하여
 * {@link MarketTrendOverview}로 반환한다. 아직 동향이 생성되지 않았어도 지표만으로
 * 정상 응답(generated=false)을 내려 프론트가 지표만 먼저 노출할 수 있도록 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketTrendQueryService {

    private static final TypeReference<List<InsightCard>> CARDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> KEYWORDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> CLUSTER_IDS_TYPE = new TypeReference<>() {
    };
    /** 출처 카드에 노출할 수 있는 클러스터 상태 (상세 API와 동일) */
    private static final List<SummaryStatus> VISIBLE_SUMMARY_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);
    /** trend_date 조회 기준 timezone — 생성 서비스와 동일하게 Asia/Seoul 고정 */
    private static final ZoneId TREND_ZONE = ZoneId.of("Asia/Seoul");

    private final MarketTrendRepository marketTrendRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final ObjectMapper objectMapper;

    public MarketTrendOverview getOverview() {
        List<MarketSnapshot> snapshots = marketSnapshotRepository.findAll();

        // 오늘 날짜 기준 동향만 노출. 생성 실패/주말 등으로 오늘 row가 없으면 generated=false
        Optional<MarketTrend> latest = marketTrendRepository
                .findByTrendDateAndSessionAndTitleIsNotNullAndSummaryIsNotNull(
                        LocalDate.now(TREND_ZONE), MarketTrend.SESSION_DAILY);
        if (latest.isEmpty()) {
            return MarketTrendOverview.empty(snapshots);
        }

        MarketTrend trend = latest.get();
        List<InsightCard> cards = parseList(trend.getInsightCardsJson(), CARDS_TYPE);
        List<String> keywords = parseList(trend.getRelatedKeywordsJson(), KEYWORDS_TYPE);
        List<SourceClusterInfo> sourceClusters = resolveSourceClusters(trend.getSourceClusterIdsJson());
        int sourceArticleCount = trend.getSourceArticleCount() != null ? trend.getSourceArticleCount() : 0;

        // /overview 엔드포인트 응답은 personalized 개념이 적용되지 않으므로 mode=null.
        // PersonalizedMarketTrendService가 폴백으로 재사용할 때 withMode(OVERVIEW_FALLBACK)로 갈아끼운다
        return new MarketTrendOverview(
                true,
                null,
                trend.getTrendDate(),
                trend.getTitle(),
                trend.getSummary(),
                cards,
                keywords,
                sourceClusters,
                sourceArticleCount,
                trend.getUpdatedAt(),
                snapshots
        );
    }

    /**
     * 저장된 클러스터 ID 목록을 현재 클러스터 정보로 보강한다.
     */
    private List<SourceClusterInfo> resolveSourceClusters(String json) {
        List<Long> ids = parseList(json, CLUSTER_IDS_TYPE);
        return resolveSourceClusters(ids);
    }

    /**
     * personalized 등 다른 서비스가 카드 union으로부터 직접 source cluster 메타를 만들 때 사용한다.
     */
    public List<SourceClusterInfo> resolveSourceClusters(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, NewsCluster> byId = new HashMap<>();
        newsClusterRepository
                .findByIdInAndStatusAndSummaryStatusIn(ids, ClusterStatus.ACTIVE, VISIBLE_SUMMARY_STATUSES)
                .forEach(c -> byId.put(c.getId(), c));

        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(c -> new SourceClusterInfo(c.getId(), c.getTitle(), c.getPublishedAt()))
                .toList();
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> result = objectMapper.readValue(json, type);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("[MarketTrend] JSON 파싱 실패 — 빈 리스트로 fallback: {}", e.getMessage());
            return List.of();
        }
    }
}
