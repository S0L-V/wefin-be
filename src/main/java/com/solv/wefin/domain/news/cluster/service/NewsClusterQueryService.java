package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionSourceRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
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
    // ETC는 "전체" 탭에서만 노출되므로 탭 필터 대상에서 제외
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "FINANCE", "TECH", "INDUSTRY", "ENERGY", "BIO", "CRYPTO");

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final UserNewsClusterReadRepository readRepository;
    private final ClusterSummarySectionRepository sectionRepository;
    private final ClusterSummarySectionSourceRepository sectionSourceRepository;

    /**
     * 피드 목록을 커서 기반으로 조회한다.
     *
     * @param cursorPublishedAt 커서 시각 (null이면 첫 페이지)
     * @param cursorId          커서 ID (null이면 첫 페이지)
     * @param pageSize          페이지 크기
     * @param userId            사용자 ID (null이면 isRead 항상 false)
     * @return 클러스터 목록 + 부가 정보
     */
    /**
     * @param tab "ALL" / "FINANCE" / "TECH" / "INDUSTRY" / "ENERGY" / "BIO" / "CRYPTO" — null이면 ALL 취급
     */
    public ClusterFeedResult getFeed(OffsetDateTime cursorPublishedAt, Long cursorId,
                                     int pageSize, UUID userId, String tab) {
        int fetchSize = pageSize + 1;
        String categoryCode = resolveCategoryCode(tab);

        List<NewsCluster> clusters = fetchClusters(cursorPublishedAt, cursorId, fetchSize, categoryCode);

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
        Map<Long, List<StockInfo>> stocksMap = allArticleIds.isEmpty()
                ? Map.of() : getRelatedStocksMap(clusterArticleMap, allArticleIds);
        Map<Long, List<String>> topicsMap = allArticleIds.isEmpty()
                ? Map.of() : getMarketTagsMap(clusterArticleMap, allArticleIds);
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
                        topicsMap.getOrDefault(c.getId(), List.of()),
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
     * tab 파라미터를 대분류 카테고리 코드로 변환한다. ALL이면 null (필터 없음).
     */
    private String resolveCategoryCode(String tab) {
        if (tab == null || tab.isBlank() || "ALL".equalsIgnoreCase(tab)) {
            return null;
        }
        String upper = tab.toUpperCase(java.util.Locale.ROOT);
        if (!VALID_CATEGORIES.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 tab 값입니다: " + tab);
        }
        return upper;
    }

    /**
     * 카테고리 필터 + 커서 조건에 따라 클러스터를 조회한다.
     */
    private List<NewsCluster> fetchClusters(OffsetDateTime cursorPublishedAt, Long cursorId,
                                             int fetchSize, String categoryCode) {
        boolean hasCursor = cursorPublishedAt != null && cursorId != null;

        if (categoryCode == null) {
            return hasCursor
                    ? newsClusterRepository.findForFeedAfterCursor(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, cursorPublishedAt, cursorId, PageRequest.of(0, fetchSize))
                    : newsClusterRepository.findForFeedFirstPage(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, PageRequest.of(0, fetchSize));
        } else {
            return hasCursor
                    ? newsClusterRepository.findForFeedByCategoryAfterCursor(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, TagType.SECTOR, categoryCode, cursorPublishedAt, cursorId, PageRequest.of(0, fetchSize))
                    : newsClusterRepository.findForFeedByCategoryFirstPage(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, TagType.SECTOR, categoryCode, PageRequest.of(0, fetchSize));
        }
    }

    /**
     * 클러스터별 출처(publisher) 상위 N개를 조회한다.
     *
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

                if (projection != null
                        && projection.getPublisherName() != null
                        && seenPublishers.add(projection.getPublisherName())) {

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
     * 클러스터별 관련 STOCK 태그를 조회한다. (code + name)
     */
    private Map<Long, List<StockInfo>> getRelatedStocksMap(Map<Long, List<Long>> clusterArticleMap,
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
     * 클러스터별 TOPIC 태그(marketTags)를 조회한다.
     */
    private Map<Long, List<String>> getMarketTagsMap(Map<Long, List<Long>> clusterArticleMap,
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

    /**
     * 클러스터 상세 정보를 조회한다.
     * 섹션(heading + body)과 섹션별 근거 기사 출처를 포함한다
     *
     * @param clusterId 클러스터 ID
     * @param userId 사용자 ID (null이면 isRead false)
     * @return 클러스터 상세 정보
     */
    public ClusterDetailResult getDetail(Long clusterId, UUID userId) {
        NewsCluster cluster = newsClusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLUSTER_NOT_FOUND));

        if (cluster.getStatus() != ClusterStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CLUSTER_NOT_FOUND);
        }
        if (!VISIBLE_STATUSES.contains(cluster.getSummaryStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_NOT_FOUND);
        }

        // 클러스터-기사 매핑
        List<Long> articleIds = clusterArticleRepository.findByNewsClusterId(clusterId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();

        // 섹션 조회 (출처 우선순위 결정에 필요하므로 먼저 조회)
        List<SectionDetail> sectionDetails = buildSectionDetails(clusterId);

        // 섹션 출처에 등장하는 기사 ID 수집 (top-level 출처 우선순위용)
        Set<Long> sectionSourceArticleIds = sectionDetails.stream()
                .flatMap(s -> s.sources().stream())
                .map(ArticleSourceInfo::articleId)
                .collect(Collectors.toSet());

        // 출처 (섹션 출처 기사 우선, 상위 N개, 언론사 중복 제거)
        List<ArticleSourceInfo> sources = articleIds.isEmpty()
                ? List.of()
                : getDetailSources(articleIds, sectionSourceArticleIds);

        // 관련 종목
        Map<Long, List<Long>> clusterArticleMap = Map.of(clusterId, articleIds);
        List<StockInfo> stocks = articleIds.isEmpty()
                ? List.of()
                : getRelatedStocksMap(clusterArticleMap, articleIds).getOrDefault(clusterId, List.of());

        // 마켓 태그
        List<String> marketTags = articleIds.isEmpty()
                ? List.of()
                : getMarketTagsMap(clusterArticleMap, articleIds).getOrDefault(clusterId, List.of());

        // 읽음 여부
        boolean isRead = userId != null
                && readRepository.existsByUserIdAndNewsClusterId(userId, clusterId);

        return new ClusterDetailResult(
                cluster.getId(), cluster.getTitle(), cluster.getSummary(),
                cluster.getThumbnailUrl(), cluster.getPublishedAt(),
                cluster.getArticleCount(), sources, stocks, marketTags, isRead,
                sectionDetails
        );
    }

    /**
     * 상세 조회용 출처 기사 목록을 반환한다 (title 포함, 상위 N개, 언론사 중복 제거)
     * 섹션 출처에 등장하는 기사를 우선 배치하여 top-level 출처와 섹션 출처가 일관되도록 한다
     */
    private List<ArticleSourceInfo> getDetailSources(List<Long> articleIds,
                                                      Set<Long> sectionSourceArticleIds) {
        Map<Long, NewsArticleRepository.ArticleSourceProjection> projectionMap =
                newsArticleRepository.findArticleSourceInfoByIdIn(articleIds).stream()
                        .collect(Collectors.toMap(
                                NewsArticleRepository.ArticleSourceProjection::getId, p -> p));

        // 섹션 출처 기사 우선, 나머지 후순위
        List<NewsArticleRepository.ArticleSourceProjection> sorted = new ArrayList<>();
        for (Long id : articleIds) {
            NewsArticleRepository.ArticleSourceProjection p = projectionMap.get(id);
            if (p != null && sectionSourceArticleIds.contains(id)) {
                sorted.add(p);
            }
        }
        for (Long id : articleIds) {
            NewsArticleRepository.ArticleSourceProjection p = projectionMap.get(id);
            if (p != null && !sectionSourceArticleIds.contains(id)) {
                sorted.add(p);
            }
        }

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

    /**
     * 클러스터의 섹션 + 섹션별 출처 기사를 조회한다
     */
    private List<SectionDetail> buildSectionDetails(Long clusterId) {
        List<ClusterSummarySection> sections = sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(clusterId);
        if (sections.isEmpty()) {
            return List.of();
        }

        // 섹션 ID 목록
        List<Long> sectionIds = sections.stream().map(ClusterSummarySection::getId).toList();

        // 섹션-기사 매핑 일괄 조회
        List<ClusterSummarySectionSource> allSources = sectionSourceRepository.findByClusterSummarySectionIdIn(sectionIds);

        // sectionId → articleIds
        Map<Long, List<Long>> sectionArticleMap = allSources.stream()
                .collect(Collectors.groupingBy(
                        ClusterSummarySectionSource::getClusterSummarySectionId,
                        Collectors.mapping(ClusterSummarySectionSource::getNewsArticleId, Collectors.toList())));

        // 모든 기사 ID로 기사 정보 일괄 조회
        List<Long> allArticleIds = allSources.stream()
                .map(ClusterSummarySectionSource::getNewsArticleId)
                .distinct()
                .toList();

        Map<Long, NewsArticleRepository.ArticleSourceProjection> articleMap = allArticleIds.isEmpty()
                ? Map.of()
                : newsArticleRepository.findArticleSourceInfoByIdIn(allArticleIds).stream()
                        .collect(Collectors.toMap(NewsArticleRepository.ArticleSourceProjection::getId, p -> p));

        return sections.stream()
                .map(section -> {
                    List<Long> sourceArticleIds = sectionArticleMap.getOrDefault(section.getId(), List.of());
                    List<ArticleSourceInfo> articleSources = sourceArticleIds.stream()
                            .map(articleMap::get)
                            .filter(Objects::nonNull)
                            .map(p -> new ArticleSourceInfo(p.getId(), p.getTitle(), p.getPublisherName(), p.getOriginalUrl()))
                            .toList();

                    return new SectionDetail(
                            section.getSectionOrder(), section.getHeading(), section.getBody(),
                            articleSources.size(), articleSources
                    );
                })
                .toList();
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
            int sourceCount,
            List<SourceInfo> sources,
            List<StockInfo> relatedStocks,
            List<String> marketTags,
            boolean isRead
    ) {
    }

    public record StockInfo(String code, String name) {}

    /**
     * 출처 정보 (언론사, 원본 URL)
     */
    public record SourceInfo(String publisherName, String url) {
    }

    /**
     * 클러스터 상세 조회 결과
     */
    public record ClusterDetailResult(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            int sourceCount,
            List<ArticleSourceInfo> sources,
            List<StockInfo> relatedStocks,
            List<String> marketTags,
            boolean isRead,
            List<SectionDetail> sections
    ) {
    }

    /**
     * 섹션 상세 정보 (소제목 + 단락 + 근거 기사)
     */
    public record SectionDetail(
            int sectionOrder,
            String heading,
            String body,
            int sourceCount,
            List<ArticleSourceInfo> sources
    ) {
    }

    /**
     * 섹션 출처 카드용 기사 정보
     */
    public record ArticleSourceInfo(
            Long articleId,
            String title,
            String publisherName,
            String url
    ) {
    }
}
