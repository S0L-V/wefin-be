package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.cluster.entity.ClusterSuggestedQuestion;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.HotAggregationMeta;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.ClusterSuggestedQuestionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionSourceRepository;
import com.solv.wefin.domain.news.cluster.repository.HotAggregationMetaRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.news.cluster.dto.ArticleSourceInfo;
import com.solv.wefin.domain.news.cluster.dto.SectorInfo;
import com.solv.wefin.domain.news.cluster.dto.SourceInfo;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    // ETC는 "전체" 탭에서만 노출되므로 탭 필터 대상에서 제외
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "FINANCE", "TECH", "INDUSTRY", "ENERGY", "BIO", "CRYPTO");

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final UserNewsClusterReadRepository readRepository;
    private final UserNewsClusterFeedbackRepository feedbackRepository;
    private final ClusterSuggestedQuestionRepository questionRepository;
    private final ClusterSummarySectionRepository sectionRepository;
    private final ClusterSummarySectionSourceRepository sectionSourceRepository;
    private final HotAggregationMetaRepository hotAggregationMetaRepository;
    private final ClusterTagAggregator tagAggregator;

    /**
     * 피드 목록을 커서 기반으로 조회한다.
     *
     * @param cursorTime 커서 시각 (null이면 첫 페이지)
     * @param cursorId   커서 ID (null이면 첫 페이지)
     * @param pageSize   페이지 크기
     * @param userId     사용자 ID (null이면 isRead 항상 false)
     * @param tab        "ALL" / "FINANCE" / "TECH" / "INDUSTRY" / "ENERGY" / "BIO" / "CRYPTO" — null이면 ALL 취급
     * @param sortBy     정렬 기준 ("publishedAt" 또는 "updatedAt", 기본 publishedAt)
     * @return 클러스터 목록 + 부가 정보
     */
    public ClusterFeedResult getFeed(OffsetDateTime cursorTime, Long cursorId,
                                     int pageSize, UUID userId, String tab, String sortBy) {
        return getFeed(cursorTime, cursorId, pageSize, userId, tab, sortBy, null, null);
    }

    /**
     * 피드 목록을 커서 기반으로 조회한다.
     *
     * tagType + tagCodes가 주어지면 해당 태그들로 필터링하고, 아니면 tab 기반 카테고리 필터를 사용한다
     *
     * @param tagType 태그 유형 (null이면 무시)
     * @param tagCodes 태그 코드 목록 (null이면 무시)
     */
    public ClusterFeedResult getFeed(OffsetDateTime cursorTime, Long cursorId,
                                     int pageSize, UUID userId, String tab, String sortBy,
                                     TagType tagType, List<String> tagCodes) {
        SortMode sortMode = SortMode.from(sortBy);

        List<String> filterTagCodes;
        TagType filterTagType;
        if (tagType != null && tagCodes != null && !tagCodes.isEmpty()) {
            filterTagType = tagType;
            filterTagCodes = tagCodes;
        } else {
            String categoryCode = resolveCategoryCode(tab);
            filterTagType = categoryCode != null ? TagType.SECTOR : null;
            filterTagCodes = categoryCode != null ? List.of(categoryCode) : null;
        }

        // sort=view: 페이지네이션 미지원 — Top N 고정, cursor 무시, hasNext=false
        // 커서 갱신으로 인한 정렬 키 흔들림(페이지 중복/누락)을 원천 차단
        int fetchSize = sortMode == SortMode.VIEW ? pageSize : pageSize + 1;
        List<NewsCluster> clusters = fetchClusters(cursorTime, cursorId, fetchSize,
                filterTagType, filterTagCodes, sortMode);

        boolean hasNext = sortMode != SortMode.VIEW && clusters.size() > pageSize;
        if (hasNext) {
            clusters = clusters.subList(0, pageSize);
        }

        OffsetDateTime lastAggregatedAt = sortMode == SortMode.VIEW
                ? hotAggregationMetaRepository.findSingleton()
                        .map(HotAggregationMeta::getLastSuccessAt).orElse(null)
                : null;

        if (clusters.isEmpty()) {
            return ClusterFeedResult.empty(lastAggregatedAt);
        }

        /* 부가 정보 일괄 조회 */

        // 클러스터 ID 목록
        List<Long> clusterIds = clusters.stream().map(NewsCluster::getId).toList();

        // clusterId → articleIds 매핑
        Map<Long, List<Long>> clusterArticleMap = buildClusterArticleMap(clusterIds);

        // 모든 기사 ID flat 추출 (중복 제거)
        List<Long> allArticleIds = clusterArticleMap.values().stream().flatMap(List::stream).distinct().toList();

        // 출처 / 종목 / 토픽 / 읽음 여부 일괄 조회 (빈 리스트면 IN 절 오류 방지)
        Map<Long, List<SourceInfo>> sourcesMap = allArticleIds.isEmpty()
                ? Map.of() : tagAggregator.aggregateSources(clusterArticleMap, allArticleIds);
        Map<Long, List<StockInfo>> stocksMap = allArticleIds.isEmpty()
                ? Map.of() : tagAggregator.aggregateStocks(clusterArticleMap, allArticleIds);
        Map<Long, List<String>> topicsMap = allArticleIds.isEmpty()
                ? Map.of() : tagAggregator.aggregateMarketTags(clusterArticleMap, allArticleIds);
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
                        readClusterIds.contains(c.getId()),
                        c.getRecentViewCount()
                ))
                .toList();

        // 다음 커서 생성 (sort=view 는 커서 없음)
        if (!hasNext) {
            return new ClusterFeedResult(items, false, null, null, lastAggregatedAt);
        }

        NewsCluster last = clusters.get(clusters.size() - 1);
        OffsetDateTime nextCursorTime = sortMode == SortMode.UPDATED_AT
                ? last.getUpdatedAt() : last.getPublishedAt();

        return new ClusterFeedResult(items, true, nextCursorTime, last.getId(), lastAggregatedAt);
    }

    /** 내부 정렬 모드 표현 — 컨트롤러 sort 문자열을 한 번만 해석한다 */
    private enum SortMode {
        PUBLISHED_AT, UPDATED_AT, VIEW;

        static SortMode from(String sortBy) {
            if ("updatedAt".equalsIgnoreCase(sortBy)) return UPDATED_AT;
            if ("view".equalsIgnoreCase(sortBy)) return VIEW;
            return PUBLISHED_AT;
        }
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
     * 태그 필터 + 커서 조건 + 정렬 기준에 따라 클러스터를 조회한다.
     *
     * sort=view 는 cursor 를 무시하고 Top N 을 반환한다 (페이지네이션 미지원).
     *
     * @param filterTagType 태그 유형 (null이면 필터 없음)
     * @param filterTagCodes 태그 코드 목록 (null 또는 빈 리스트면 필터 없음)
     */
    private List<NewsCluster> fetchClusters(OffsetDateTime cursorTime, Long cursorId,
                                             int fetchSize, TagType filterTagType,
                                             List<String> filterTagCodes, SortMode sortMode) {
        Pageable pageable = PageRequest.of(0, fetchSize);
        boolean hasTagFilter = filterTagType != null
                && filterTagCodes != null && !filterTagCodes.isEmpty();

        if (sortMode == SortMode.VIEW) {
            return hasTagFilter
                    ? newsClusterRepository.findHotClustersByTags(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, filterTagType, filterTagCodes, pageable)
                    : newsClusterRepository.findHotClusters(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, pageable);
        }

        boolean hasCursor = cursorTime != null && cursorId != null;
        boolean sortByUpdatedAt = sortMode == SortMode.UPDATED_AT;

        if (!hasTagFilter) {
            if (sortByUpdatedAt) {
                return hasCursor
                        ? newsClusterRepository.findForFeedAfterCursorByUpdatedAt(
                                ClusterStatus.ACTIVE, VISIBLE_STATUSES, cursorTime, cursorId, pageable)
                        : newsClusterRepository.findForFeedFirstPageByUpdatedAt(
                                ClusterStatus.ACTIVE, VISIBLE_STATUSES, pageable);
            }
            return hasCursor
                    ? newsClusterRepository.findForFeedAfterCursorByPublishedAt(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, cursorTime, cursorId, pageable)
                    : newsClusterRepository.findForFeedFirstPageByPublishedAt(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, pageable);
        }

        if (sortByUpdatedAt) {
            return hasCursor
                    ? newsClusterRepository.findForFeedByTagsAfterCursorByUpdatedAt(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, filterTagType, filterTagCodes, cursorTime, cursorId, pageable)
                    : newsClusterRepository.findForFeedByTagsFirstPageByUpdatedAt(
                            ClusterStatus.ACTIVE, VISIBLE_STATUSES, filterTagType, filterTagCodes, pageable);
        }
        return hasCursor
                ? newsClusterRepository.findForFeedByTagsAfterCursorByPublishedAt(
                        ClusterStatus.ACTIVE, VISIBLE_STATUSES, filterTagType, filterTagCodes, cursorTime, cursorId, pageable)
                : newsClusterRepository.findForFeedByTagsFirstPageByPublishedAt(
                        ClusterStatus.ACTIVE, VISIBLE_STATUSES, filterTagType, filterTagCodes, pageable);
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
                : tagAggregator.aggregateDetailSources(articleIds, sectionSourceArticleIds);

        // 관련 종목 / 관련 분야 / 마켓 태그 — 단건 전용 오버로드 사용 (Map 래핑 불필요)
        List<StockInfo> stocks = tagAggregator.aggregateStocksForCluster(articleIds);
        List<SectorInfo> sectors = tagAggregator.aggregateSectorsForCluster(articleIds);
        List<String> marketTags = tagAggregator.aggregateMarketTagsForCluster(articleIds);

        // 읽음 여부
        boolean isRead = userId != null
                && readRepository.existsByUserIdAndNewsClusterId(userId, clusterId);

        // 피드백 여부
        String feedbackType = null;
        if (userId != null) {
            feedbackType = feedbackRepository.findByUserIdAndNewsClusterId(userId, clusterId)
                    .map(fb -> fb.getFeedbackType().name())
                    .orElse(null);
        }

        // 추천 질문
        List<String> suggestedQuestions = questionRepository
                .findByNewsClusterIdOrderByQuestionOrder(clusterId)
                .stream().map(ClusterSuggestedQuestion::getQuestion).toList();

        // 단독 클러스터(기사 1건 + 섹션 없음)는 기사 전문을 내려준다
        String articleContent = null;
        if (articleIds.size() == 1 && sectionDetails.isEmpty()) {
            articleContent = newsArticleRepository.findById(articleIds.get(0))
                    .map(article -> article.getContent())
                    .orElse(null);
        }

        return new ClusterDetailResult(
                cluster.getId(), cluster.getTitle(), cluster.getSummary(),
                cluster.getThumbnailUrl(), cluster.getPublishedAt(),
                cluster.getArticleCount(), sources, stocks, sectors, marketTags, isRead,
                feedbackType, sectionDetails, suggestedQuestions, articleContent
        );
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
     * 커서 기반 페이징 결과.
     *
     * sort=view 응답은 {@code hasNext=false}, cursor 필드는 null 이며
     * {@code lastAggregatedAt} 에 배치 마지막 성공 시각이 들어간다
     * (첫 배포 전이면 null — FE가 "준비 중" UX 또는 폴백 처리).
     */
    public record ClusterFeedResult(
            List<ClusterFeedItem> items,
            boolean hasNext,
            OffsetDateTime nextCursorPublishedAt,
            Long nextCursorId,
            OffsetDateTime lastAggregatedAt
    ) {
        public static ClusterFeedResult empty(OffsetDateTime lastAggregatedAt) {
            return new ClusterFeedResult(List.of(), false, null, null, lastAggregatedAt);
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
            boolean isRead,
            long recentViewCount
    ) {
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
            List<SectorInfo> relatedSectors,
            List<String> marketTags,
            boolean isRead,
            String feedbackType,
            List<SectionDetail> sections,
            List<String> suggestedQuestions,
            String articleContent
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

}
