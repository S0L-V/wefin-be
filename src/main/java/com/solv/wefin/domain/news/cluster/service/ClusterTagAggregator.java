package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.dto.ArticleSourceInfo;
import com.solv.wefin.domain.news.cluster.dto.SourceInfo;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 클러스터별 태그/출처 집계 전담 컴포넌트
 *
 * 피드 목록과 상세 조회 양쪽에서 공통으로 쓰이는 부가 정보(출처, 관련 종목,
 * 마켓 태그, 상세용 출처 카드) 집계 로직을 담는다. {@link NewsClusterQueryService}의
 * 책임 크기를 줄이고 집계 단위의 단위 테스트를 가능하게 한다
 */
@Component
@RequiredArgsConstructor
public class ClusterTagAggregator {

    private static final int MAX_SOURCES_PER_CLUSTER = 3;

    private final NewsArticleRepository newsArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;

    /**
     * 클러스터별 출처(언론사) 상위 N개를 집계한다
     *
     * 정책: 같은 언론사는 1번만 포함(publisherName 기준 dedup), 최대
     * {@link #MAX_SOURCES_PER_CLUSTER}개까지
     *
     * @param clusterArticleMap clusterId → 소속 articleIds
     * @param allArticleIds     IN 절 조회용 전체 기사 ID
     * @return clusterId → 출처 목록
     */
    public Map<Long, List<SourceInfo>> aggregateSources(Map<Long, List<Long>> clusterArticleMap,
                                                        List<Long> allArticleIds) {
        Map<Long, NewsArticleRepository.SourceProjection> projectionMap =
                newsArticleRepository.findSourceInfoByIdIn(allArticleIds).stream()
                        .collect(Collectors.toMap(NewsArticleRepository.SourceProjection::getId, p -> p));

        Map<Long, List<SourceInfo>> result = new HashMap<>();

        for (var entry : clusterArticleMap.entrySet()) {
            Set<String> seenPublishers = new HashSet<>();
            List<SourceInfo> sources = new ArrayList<>();

            for (Long articleId : entry.getValue()) {
                var projection = projectionMap.get(articleId);

                if (projection != null
                        && projection.getPublisherName() != null
                        && seenPublishers.add(projection.getPublisherName())) {

                    sources.add(new SourceInfo(projection.getPublisherName(), projection.getOriginalUrl()));

                    if (sources.size() >= MAX_SOURCES_PER_CLUSTER) break;
                }
            }
            result.put(entry.getKey(), sources);
        }
        return result;
    }

    /**
     * 단일 클러스터용 — 기사 목록의 관련 STOCK 태그를 집계한다
     */
    public List<StockInfo> aggregateStocksForCluster(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return List.of();
        }
        Map<String, StockInfo> seen = new LinkedHashMap<>();
        List<NewsArticleTag> stockTags = articleTagRepository.findByNewsArticleIdInAndTagType(
                articleIds, TagType.STOCK);
        for (NewsArticleTag t : stockTags) {
            seen.putIfAbsent(t.getTagCode(), new StockInfo(t.getTagCode(), t.getTagName()));
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * 단일 클러스터용 — 기사 목록의 TOPIC 태그명을 집계한다
     */
    public List<String> aggregateMarketTagsForCluster(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<NewsArticleTag> topicTags = articleTagRepository.findByNewsArticleIdInAndTagType(
                articleIds, TagType.TOPIC);
        for (NewsArticleTag t : topicTags) {
            seen.add(t.getTagName());
        }
        return new ArrayList<>(seen);
    }

    /**
     * 클러스터별 관련 STOCK 태그(code + name)를 집계한다
     */
    public Map<Long, List<StockInfo>> aggregateStocks(Map<Long, List<Long>> clusterArticleMap,
                                                      List<Long> allArticleIds) {
        List<NewsArticleTag> stockTags = articleTagRepository.findByNewsArticleIdInAndTagType(
                allArticleIds, TagType.STOCK);
        Map<Long, List<NewsArticleTag>> tagsByArticle = stockTags.stream()
                .collect(Collectors.groupingBy(NewsArticleTag::getNewsArticleId));

        Map<Long, List<StockInfo>> result = new HashMap<>();
        for (var entry : clusterArticleMap.entrySet()) {
            Map<String, StockInfo> seen = new LinkedHashMap<>();
            for (Long articleId : entry.getValue()) {
                tagsByArticle.getOrDefault(articleId, List.of()).forEach(t ->
                        seen.putIfAbsent(t.getTagCode(), new StockInfo(t.getTagCode(), t.getTagName())));
            }
            result.put(entry.getKey(), new ArrayList<>(seen.values()));
        }
        return result;
    }

    /**
     * 클러스터별 마켓 태그(TOPIC 태그명)를 집계한다
     */
    public Map<Long, List<String>> aggregateMarketTags(Map<Long, List<Long>> clusterArticleMap,
                                                       List<Long> allArticleIds) {
        List<NewsArticleTag> topicTags = articleTagRepository.findByNewsArticleIdInAndTagType(
                allArticleIds, TagType.TOPIC);
        Map<Long, List<NewsArticleTag>> tagsByArticle = topicTags.stream()
                .collect(Collectors.groupingBy(NewsArticleTag::getNewsArticleId));

        Map<Long, List<String>> result = new HashMap<>();
        for (var entry : clusterArticleMap.entrySet()) {
            Set<String> topics = new LinkedHashSet<>();
            for (Long articleId : entry.getValue()) {
                tagsByArticle.getOrDefault(articleId, List.of())
                        .forEach(t -> topics.add(t.getTagName()));
            }
            result.put(entry.getKey(), new ArrayList<>(topics));
        }
        return result;
    }

    /**
     * 상세 조회용 출처 카드 목록을 반환한다 (title 포함, 상위 N개, 언론사 중복 제거)
     *
     * 섹션 출처에 등장하는 기사(prioritizedArticleIds)를 top-level 출처 앞쪽에
     * 배치하여 섹션 출처와 top-level 출처가 일관되도록 한다
     *
     * @param articleIds              클러스터 소속 기사 ID (정렬 순서 유지)
     * @param prioritizedArticleIds   섹션 출처로 선택된 기사 ID 집합 (우선 배치)
     * @return 상세 화면 출처 카드 목록 (최대 {@link #MAX_SOURCES_PER_CLUSTER}개)
     */
    public List<ArticleSourceInfo> aggregateDetailSources(List<Long> articleIds,
                                                         Set<Long> prioritizedArticleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }

        Map<Long, NewsArticleRepository.ArticleSourceProjection> projectionMap =
                newsArticleRepository.findArticleSourceInfoByIdIn(articleIds).stream()
                        .collect(Collectors.toMap(
                                NewsArticleRepository.ArticleSourceProjection::getId, p -> p));

        // 섹션 출처 기사를 앞쪽으로 (comparator 0 < 1), 동순위는 articleIds 입력 순서 유지 (stable sort).
        // projection이 없는 id(삭제/크롤 실패)는 제외
        List<NewsArticleRepository.ArticleSourceProjection> sorted = articleIds.stream()
                .map(projectionMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p -> prioritizedArticleIds.contains(p.getId()) ? 0 : 1))
                .toList();

        Set<String> seenPublishers = new HashSet<>();
        List<ArticleSourceInfo> result = new ArrayList<>();

        for (NewsArticleRepository.ArticleSourceProjection p : sorted) {
            if (p.getPublisherName() != null && seenPublishers.add(p.getPublisherName())) {
                result.add(new ArticleSourceInfo(p.getId(), p.getTitle(), p.getPublisherName(), p.getOriginalUrl()));
                if (result.size() >= MAX_SOURCES_PER_CLUSTER) break;
            }
        }
        return result;
    }
}
