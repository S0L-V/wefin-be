package com.solv.wefin.domain.market.trend.service;

import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.ClusterSummary;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.ParsedCard;
import com.solv.wefin.domain.market.trend.client.OpenAiMarketTrendClient.PersonalizedParsedCard;
import com.solv.wefin.domain.market.trend.dto.InsightCard;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AI가 생성한 금융 동향 카드 응답을 도메인 모델로 변환하고ㅡ 카드 기반 출처 및 태그 메타데이터를 계산하는 매퍼 컴포넌트
 *
 * AI 응답은 clusterId가 아닌 1-based index를 기준으로 참조를 반환하므로 이를 실제 clusterId로 매핑하는 책임을 가진다.
 * 카드 단위 중복 제거, 출처 클러스터 집계, 종목/토픽 빈도 계산 등 AI 결과 후처리 로직을 서비스 레이어에서 분리하기 위해 사용된다.
 */
@Component
public class MarketTrendCardMapper {

    /**
     * AI overview 카드 응답을 InsightCard로 변환한다.
     *
     * AI가 반환한 cluster index를 실제 clusterId로 변환하며, 유효하지 않은 카드(isValid=false)는 제외한다.
     * overview 카드에는 투자 조언이 없으므로 advice 필드는 비워둔다.
     */
    public List<InsightCard> mapOverviewCards(List<ParsedCard> parsed, List<ClusterSummary> clusterSummaries) {
        List<InsightCard> result = new ArrayList<>();
        for (ParsedCard p : parsed) {
            if (p == null || !p.isValid()) continue;
            List<Long> ids = mapClusterIndices(p.relatedClusterIndices(), clusterSummaries);
            result.add(InsightCard.withoutAdvice(p.headline(), p.body(), ids));
        }
        return List.copyOf(result);
    }

    /**
     * AI personalized 카드 응답을 InsightCard로 변환한다.
     *
     * advice/adviceLabel은 AI 응답을 그대로 사용한다.
     */
    public List<InsightCard> mapPersonalizedCards(List<PersonalizedParsedCard> parsed,
                                                  List<ClusterSummary> clusterSummaries) {
        List<InsightCard> result = new ArrayList<>();
        for (PersonalizedParsedCard p : parsed) {
            if (p == null || !p.isValid()) continue;
            List<Long> ids = mapClusterIndices(p.relatedClusterIndices(), clusterSummaries);
            result.add(new InsightCard(p.headline(), p.body(), p.advice(), p.adviceLabel(), ids));
        }
        return List.copyOf(result);
    }

    /**
     * 카드들이 실제로 참조한 clusterId의 합집합을 반환한다.
     *
     * AI가 카드 생성에 사용한 클러스터만 출처로 기록하기 위해 사용된다.
     */
    public List<Long> collectReferencedClusterIds(List<InsightCard> cards) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (InsightCard card : cards) {
            ids.addAll(card.relatedClusterIds());
        }
        return List.copyOf(ids);
    }

    /**
     * 클러스터별 종목 태그를 flatten하여 등장 빈도 기준 상위 N개 종목명을 반환한다.
     *
     * 동일 종목은 code 기준으로 집계하며 name은 최초 등장 값을 사용한다.
     */
    public List<String> collectTopStockNames(Map<Long, List<StockInfo>> perCluster, int topN) {
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

    /**
     * 클러스터별 TOPIC 이름 집계에서 등장 빈도 상위 N개를 반환한다.
     */
    public List<String> collectTopTopicNames(Map<Long, List<String>> perCluster, int topN) {
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
     * AI가 반환한 1-based cluster index를 실제 clusterId로 변환한다.
     *
     * AI 응답 특성상:
     * - 동일 index가 중복될 수 있어 카드 단위에서 중복 제거 (LinkedHashSet로 순서 유지)
     * - 범위를 벗어난 index가 포함될 수 있어 방어 처리
     */
    private List<Long> mapClusterIndices(List<Integer> indices, List<ClusterSummary> clusterSummaries) {

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Integer idx : indices) {
            if (idx == null) continue;
            if (idx < 1 || idx > clusterSummaries.size()) continue;
            ids.add(clusterSummaries.get(idx - 1).clusterId());
        }
        return List.copyOf(ids);
    }
}
