package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 뉴스 클러스터 피드 목록 조회 서비스
 * <p>
 * 커서 기반 페이지네이션으로 ACTIVE + 요약 완료 클러스터를 최신순으로 반환한다.
 * 각 클러스터에 출처(publisher) 집계, 관련 종목(STOCK 태그), 읽음 여부를 포함한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsClusterQueryService {

    private static final List<SummaryStatus> VISIBLE_STATUSES = List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);
    private static final int MAX_SOURCES_PER_CLUSTER = 3;

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final UserNewsClusterReadRepository readRepository;

    /**
     * 피드 목록을 커서 기반으로 조회한다.
     *
     * @param cursorPublishedAt 커서 시각 (null이면 첫 페이지)
     * @param cursorId          커서 ID (null이면 첫 페이지)
     * @param pageSize          페이지 크기
     * @param userId            사용자 ID (null이면 isRead 항상 false)
     * @return 클러스터 목록 + 부가 정보
     */
    public ClusterFeedResult getFeed(OffsetDateTime cursorPublishedAt, Long cursorId,
                                     int pageSize, UUID userId) {
        int fetchSize = pageSize + 1;

        List<NewsCluster> clusters;

        // 커서가 있으면 이후 데이터, 없으면 첫 페이지 조회
        if (cursorPublishedAt != null && cursorId != null) {
            clusters = newsClusterRepository.findForFeedAfterCursor(
                    ClusterStatus.ACTIVE, VISIBLE_STATUSES,
                    cursorPublishedAt, cursorId,
                    PageRequest.of(0, fetchSize));
        } else {
            clusters = newsClusterRepository.findForFeedFirstPage(
                    ClusterStatus.ACTIVE, VISIBLE_STATUSES,
                    PageRequest.of(0, fetchSize));
        }

        // 다음 페이지 존재 여부
        boolean hasNext = clusters.size() > pageSize;

        // pageSize만큼 실제 반환
        if (hasNext) {
            clusters = clusters.subList(0, pageSize);
        }

        // 결과 없으면 빈 응답 반환
        if (clusters.isEmpty()) {
            return ClusterFeedResult.empty();
        }

        /* 부가 정보 일괄 조회 */

        // 클러스터 ID 목록
        List<Long> clusterIds = clusters.stream().map(NewsCluster::getId).toList();

        // clusterId → articleIds 매핑
        Map<Long, List<Long>> clusterArticleMap = buildClusterArticleMap(clusterIds);

        // 모든 기사 ID flat 추출 (중복 제거)
        List<Long> allArticleIds = clusterArticleMap.values().stream().flatMap(List::stream).distinct().toList();

        // 출처 / 종목 / 읽음 여부 일괄 조회 (빈 리스트면 IN 절 오류 방지)
        Map<Long, List<SourceInfo>> sourcesMap = allArticleIds.isEmpty()
                ? Map.of() : getSourcesMap(clusterArticleMap, allArticleIds);
        Map<Long, List<String>> stocksMap = allArticleIds.isEmpty()
                ? Map.of() : getRelatedStocksMap(clusterArticleMap, allArticleIds);
        Set<Long> readClusterIds = getReadClusterIds(clusterIds, userId);

        // 클러스터 → 응답 DTO 변환
        List<ClusterFeedItem> items = clusters.stream()
                .map(c -> new ClusterFeedItem(
                        c.getId(),
                        c.getTitle(),
                        c.getSummary(),
                        c.getThumbnailUrl(),
                        c.getPublishedAt(),
                        c.getArticleCount(),
                        sourcesMap.getOrDefault(c.getId(), List.of()),
                        stocksMap.getOrDefault(c.getId(), List.of()),
                        readClusterIds.contains(c.getId())
                ))
                .toList();

        // 다음 커서 생성 (마지막 데이터 기준)
        NewsCluster last = clusters.get(clusters.size() - 1);

        return new ClusterFeedResult(items, hasNext, last.getPublishedAt(), last.getId());
    }

    /**
     * 클러스터-기사 매핑을 한 번에 조회하여 clusterId → articleIds Map 생성
     */
    private Map<Long, List<Long>> buildClusterArticleMap(List<Long> clusterIds) {
        List<NewsClusterArticle> allMappings = clusterArticleRepository.findByNewsClusterIdIn(clusterIds);
        return allMappings.stream()
                .collect(Collectors.groupingBy(
                        NewsClusterArticle::getNewsClusterId,
                        Collectors.mapping(NewsClusterArticle::getNewsArticleId, Collectors.toList())));
    }

    /**
     * 클러스터별 출처(publisher) 상위 N개를 조회한다.
     * <p>
     * 정책:
     * - 같은 언론사는 1번만 포함 (publisherName 기준 dedup)
     * - 최대 MAX_SOURCES_PER_CLUSTER 개까지만 노출
     */
    private Map<Long, List<SourceInfo>> getSourcesMap(Map<Long, List<Long>> clusterArticleMap,
                                                      List<Long> allArticleIds) {
        // projection 조회 (엔티티 전체 로딩 방지)
        Map<Long, NewsArticleRepository.SourceProjection> projectionMap =
                newsArticleRepository.findSourceInfoByIdIn(allArticleIds).stream()
                        .collect(Collectors.toMap(NewsArticleRepository.SourceProjection::getId, p -> p));

        Map<Long, List<SourceInfo>> result = new HashMap<>();

        // clusterId별로 순회
        for (var entry : clusterArticleMap.entrySet()) {
            Set<String> seenPublishers = new HashSet<>(); // publisherName 기준 중복 제거용 Set
            List<SourceInfo> sources = new ArrayList<>(); // 최종 출처 리스트

            for (Long articleId : entry.getValue()) {
                var projection = projectionMap.get(articleId); // O(1)로 projection 조회

                // null 방어 + publisher 기준 dedup
                if (projection != null && seenPublishers.add(projection.getPublisherName())) {

                    // 출처 정보 추가
                    sources.add(new SourceInfo(projection.getPublisherName(), projection.getOriginalUrl()));

                    // 최대 개수 제한
                    if (sources.size() >= MAX_SOURCES_PER_CLUSTER) break;
                }
            }
            result.put(entry.getKey(), sources);
        }
        return result;
    }

    /**
     * 클러스터별 관련 STOCK 태그를 조회한다.
     */
    private Map<Long, List<String>> getRelatedStocksMap(Map<Long, List<Long>> clusterArticleMap,
                                                        List<Long> allArticleIds) {
        // STOCK 태그만 DB에서 조회
        List<NewsArticleTag> stockTags = articleTagRepository.findByNewsArticleIdInAndTagType(
                allArticleIds, TagType.STOCK);

        // articleId → STOCK 태그 목록 매핑
        Map<Long, List<NewsArticleTag>> tagsByArticle = stockTags.stream()
                .collect(Collectors.groupingBy(NewsArticleTag::getNewsArticleId));

        Map<Long, List<String>> result = new HashMap<>();

        // clusterId별로 순회
        for (var entry : clusterArticleMap.entrySet()) {
            Set<String> stocks = new LinkedHashSet<>(); // 종목명 dedup + 순서 유지

            // 해당 클러스터에 포함된 기사 순회
            for (Long articleId : entry.getValue()) {

                // 해당 기사에 매핑된 STOCK 태그 조회
                List<NewsArticleTag> tags =
                        tagsByArticle.getOrDefault(articleId, List.of());

                // 태그명만 추출하여 Set에 추가 (중복 자동 제거)
                tags.forEach(t -> stocks.add(t.getTagName()));
            }

            result.put(entry.getKey(), new ArrayList<>(stocks));
        }
        return result;
    }

    /**
     * 사용자가 읽은 클러스터 ID Set을 반환한다.
     */
    private Set<Long> getReadClusterIds(List<Long> clusterIds, UUID userId) {

        // 비회원인 경우 읽음 여부 계산 불필요 → 빈 Set 반환
        if (userId == null) {
            return Set.of();
        }

        return readRepository.findByUserIdAndNewsClusterIdIn(userId, clusterIds).stream()
                .map(UserNewsClusterRead::getNewsClusterId)
                .collect(Collectors.toSet());
    }

    // --- Result Records ---

    /**
     * 커서 기반 페이징 결과
     */
    public record ClusterFeedResult(
            List<ClusterFeedItem> items,
            boolean hasNext,
            OffsetDateTime nextCursorPublishedAt,
            Long nextCursorId
    ) {
        public static ClusterFeedResult empty() {
            return new ClusterFeedResult(List.of(), false, null, null);
        }
    }

    /**
     * 클러스터 피드 아이템
     */
    public record ClusterFeedItem(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            int sourceCount, // 클러스터에 포함된 전체 기사 수
            List<SourceInfo> sources, // 대표 출처 (최대 3개)
            List<String> relatedStocks, // 관련 종목 태그
            boolean isRead // 사용자 읽음 여부
    ) {
    }

    /**
     * 출처 정보 (언론사, 원본 URL)
     */
    public record SourceInfo(String publisherName, String url) {
    }
}
