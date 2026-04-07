package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.service.ArticleVectorService;
import com.solv.wefin.domain.news.cluster.service.ClusterMatchingService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요약 직전 이상치 제거 서비스
 *
 * centroid 유사도 하위 + 태그 불일치 기사를 클러스터 매핑에서 제거하고,
 * 클러스터 집계 상태(articleCount, centroid)를 재계산한다.
 */
@Slf4j
@Service
public class OutlierDetectionService {

    private static final double CATEGORY_DOMINANCE_THRESHOLD = 0.60;

    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleVectorService articleVectorService;
    private final ClusterMatchingService clusterMatchingService;

    private final double similarityThreshold;
    private final double hardThreshold;
    private final Set<String> broadTopicBlacklist;

    public OutlierDetectionService(
            NewsClusterArticleRepository clusterArticleRepository,
            NewsClusterRepository newsClusterRepository,
            NewsArticleTagRepository articleTagRepository,
            NewsArticleRepository newsArticleRepository,
            ArticleVectorService articleVectorService,
            ClusterMatchingService clusterMatchingService,
            @Value("${clustering.outlier.similarity-threshold:0.70}") double similarityThreshold,
            @Value("${clustering.outlier.hard-threshold:0.55}") double hardThreshold,
            @Value("${clustering.outlier.broad-topic-blacklist:}") List<String> broadTopicBlacklist) {
        this.clusterArticleRepository = clusterArticleRepository;
        this.newsClusterRepository = newsClusterRepository;
        this.articleTagRepository = articleTagRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.articleVectorService = articleVectorService;
        this.clusterMatchingService = clusterMatchingService;
        if (hardThreshold >= similarityThreshold) {
            throw new IllegalArgumentException(
                    "hardThreshold(" + hardThreshold + ")는 similarityThreshold(" + similarityThreshold + ")보다 작아야 합니다");
        }
        this.similarityThreshold = similarityThreshold;
        this.hardThreshold = hardThreshold;
        this.broadTopicBlacklist = broadTopicBlacklist.stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 클러스터에서 이상치 기사를 제거하고 집계 상태를 갱신한다.
     *
     * 제거된 기사는 같은 트랜잭션 안에서 즉시 단독 클러스터로 승격되어 orphan 상태가 되지 않도록 한다.
     *
     * @param cluster 대상 클러스터 (id 참조 용도, detached 허용)
     * @return 제거된 기사 수
     */
    @Transactional
    public int removeOutliers(NewsCluster cluster) {
        // 0. 현재 트랜잭션의 영속 컨텍스트에서 관리되는 엔티티로 re-fetch.
        NewsCluster managedCluster = newsClusterRepository.findById(cluster.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUMMARY_CLUSTER_NOT_FOUND));

        // 1. 클러스터에 속한 기사-매핑 조회
        List<NewsClusterArticle> mappings = clusterArticleRepository.findByNewsClusterId(managedCluster.getId());

        // 기사 1개 이하 → 이상치 판단 의미 없음
        if (mappings.size() <= 1) {
            return 0;
        }

        // 2. centroid(클러스터 중심 벡터) 조회
        float[] centroid = managedCluster.getCentroidVector();

        // centroid 없으면 유사도 계산 불가 → 종료
        if (centroid == null) {
            return 0;
        }

        // 3. 태그 전체 조회 (한 번만, checkCategoryDominance + findOutliers 공유)
        List<Long> articleIds = mappings.stream().map(NewsClusterArticle::getNewsArticleId).toList();
        List<NewsArticleTag> allTags = articleTagRepository.findByNewsArticleIdIn(articleIds);

        // 4. 클러스터 태그 분산 체크 (경고 로그용, 제거 로직과는 별개)
        checkCategoryDominance(managedCluster.getId(), mappings, allTags);

        // 5. 유사도 + 태그 기준으로 이상치 기사 탐색
        List<NewsClusterArticle> outliers = findOutliers(mappings, centroid, allTags);


        // 이상치 없으면 종료
        if (outliers.isEmpty()) {
            return 0;
        }

        // 5. 이상치 기사 ID 수집 (이후 집계 재계산에서 사용)
        Set<Long> outlierArticleIds = outliers.stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .collect(Collectors.toSet());

        // 6. 기존 매핑 삭제 + 즉시 단독 클러스터로 승격 (같은 트랜잭션 안에서)
        for (NewsClusterArticle outlier : outliers) {
            Long articleId = outlier.getNewsArticleId();
            clusterArticleRepository.deleteByNewsClusterIdAndNewsArticleId(managedCluster.getId(), articleId);
            promoteToSingletonCluster(articleId, managedCluster.getId());
        }

        // 7. 남은 기사 기준으로 클러스터 상태 재계산 (managed 엔티티에 dirty checking 적용)
        recalculateClusterState(managedCluster, mappings, outlierArticleIds);

        // 8. 처리 결과 로그
        log.info("이상치 제거 완료 — clusterId: {}, 제거: {}건, 남은 기사: {}건",
                managedCluster.getId(), outliers.size(), mappings.size() - outliers.size());

        // 9. 제거된 기사 수 반환
        return outliers.size();
    }

    /**
     * 이상치 기사를 새 단독 클러스터로 승격시킨다.
     */
    private void promoteToSingletonCluster(Long articleId, Long fromClusterId) {
        float[] vector = articleVectorService.calculateRepresentativeVector(articleId);
        if (vector == null) {
            log.warn("이상치 기사의 벡터 없음 — 단독 클러스터 승격 스킵, articleId: {}, fromClusterId: {}",
                    articleId, fromClusterId);
            return;
        }

        NewsArticle article = newsArticleRepository.findById(articleId).orElse(null);
        if (article == null) {
            log.warn("이상치 기사 조회 실패 — 단독 클러스터 승격 스킵, articleId: {}, fromClusterId: {}",
                    articleId, fromClusterId);
            return;
        }

        NewsCluster singleton = NewsCluster.createSingle(
                vector, articleId, article.getThumbnailUrl(), article.getPublishedAt());
        NewsCluster saved = newsClusterRepository.save(singleton);
        clusterArticleRepository.save(NewsClusterArticle.create(saved.getId(), articleId, 0, false));

        log.info("이상치 제거 → 단독 클러스터 승격 — articleId: {}, fromClusterId: {}, newClusterId: {}",
                articleId, fromClusterId, saved.getId());
    }

    /**
     * 이상치 제거 후 남은 기사로 클러스터 집계 상태를 재계산한다.
     * articleCount, centroid, 대표 기사(최신)를 갱신한다.
     */
    private void recalculateClusterState(NewsCluster cluster,
                                         List<NewsClusterArticle> allMappings,
                                         Set<Long> outlierArticleIds) {
        // 1. 이상치 제거 후 남은 기사 ID 추출
        List<Long> remainingArticleIds = allMappings.stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .filter(id -> !outlierArticleIds.contains(id))
                .toList();

        // 남은 기사 없으면 집계를 초기화하고 클러스터를 INACTIVE로 내려 재조회 대상에서 제외한다.
        if (remainingArticleIds.isEmpty()) {
            cluster.recalculateAggregates(0, null, null, null, null);
            cluster.deactivate();
            return;
        }

        // 2. centroid 재계산 (남은 기사 벡터 평균)
        List<float[]> remainingVectors = remainingArticleIds.stream()
                .map(articleVectorService::calculateRepresentativeVector)
                .filter(v -> v != null)
                .toList();

        float[] newCentroid = remainingVectors.isEmpty() ? null : averageVectors(remainingVectors);

        // 3. 대표 기사 재선정. 기본은 최신 기사(publishedAt 최댓값).
        //    모든 기사의 publishedAt이 null이면 대표 메타데이터가 사라지는 걸 막기 위해
        //    남은 기사 중 임의의 한 건을 fallback으로 사용한다.
        List<NewsArticle> remainingArticles = newsArticleRepository.findAllById(remainingArticleIds);
        NewsArticle representative = remainingArticles.stream()
                .filter(a -> a.getPublishedAt() != null)
                .max((a, b) -> a.getPublishedAt().compareTo(b.getPublishedAt()))
                .orElseGet(() -> remainingArticles.stream().findAny().orElse(null));

        // 4. 클러스터 상태 업데이트
        cluster.recalculateAggregates(
                remainingArticleIds.size(),
                newCentroid,
                representative != null ? representative.getId() : null,
                representative != null ? representative.getPublishedAt() : null,
                representative != null ? representative.getThumbnailUrl() : null
        );
    }

    /**
     * 여러 벡터의 평균을 계산 (centroid 생성)
     */
    private float[] averageVectors(List<float[]> vectors) {
        int dimension = vectors.get(0).length;
        float[] sum = new float[dimension];

        for (float[] vector : vectors) {
            for (int i = 0; i < dimension; i++) {
                sum[i] += vector[i];
            }
        }

        float count = vectors.size();
        float[] average = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            average[i] = sum[i] / count;
        }
        return average;
    }

    /**
     * 이상치 기사 탐색
     * centroid와 유사도가 낮고 태그가 클러스터와 불일치하면 → outlier로 판단
     */
    private List<NewsClusterArticle> findOutliers(List<NewsClusterArticle> mappings, float[] centroid,
                                                   List<NewsArticleTag> allTags) {
        // articleId → (TagType → Set<tagCode>) 사전 인덱싱
        // 후보 기사를 제외한 "나머지 기사들의 태그 집합"을 O(1)에 구하기 위해 사용한다.
        Map<Long, Map<TagType, Set<String>>> tagsByArticle = groupTagsByArticle(allTags);

        List<NewsClusterArticle> outliers = new ArrayList<>();

        for (NewsClusterArticle mapping : mappings) {
            Long candidateId = mapping.getNewsArticleId();

            // 기사 벡터 계산
            float[] vector = articleVectorService.calculateRepresentativeVector(candidateId);
            if (vector == null) {
                continue;
            }

            // centroid와 유사도 계산
            double similarity = clusterMatchingService.cosineSimilarity(vector, centroid);

            // 1단: 극단 이상치 — 유사도가 매우 낮으면 태그 검사 없이 즉시 제거
            if (similarity < hardThreshold) {
                log.debug("극단 이상치 제거 — articleId: {}, similarity: {}", candidateId, similarity);
                outliers.add(mapping);
                continue;
            }

            // 2단: 중간 영역 — 유사도가 낮고 + 태그도 불일치하면 제거
            if (similarity < similarityThreshold) {
                Map<TagType, Set<String>> candidateTags = tagsByArticle.getOrDefault(candidateId, Map.of());
                Map<TagType, Set<String>> otherTagsByType = unionTagsExcluding(tagsByArticle, candidateId);

                if (isTagMismatch(candidateTags, otherTagsByType)) {
                    outliers.add(mapping);
                }
            }
        }

        return outliers;
    }

    /**
     * 태그 리스트를 articleId → (TagType → Set<tagCode>) 형태로 그룹핑한다.
     */
    private Map<Long, Map<TagType, Set<String>>> groupTagsByArticle(List<NewsArticleTag> allTags) {
        Map<Long, Map<TagType, Set<String>>> result = new HashMap<>();
        for (NewsArticleTag tag : allTags) {
            result
                    .computeIfAbsent(tag.getNewsArticleId(), k -> new HashMap<>())
                    .computeIfAbsent(tag.getTagType(), k -> new HashSet<>())
                    .add(tag.getTagCode());
        }
        return result;
    }

    /**
     * 특정 articleId를 제외한 나머지 기사들의 태그를 TagType별 Set으로 합친다.
     */
    private Map<TagType, Set<String>> unionTagsExcluding(
            Map<Long, Map<TagType, Set<String>>> tagsByArticle, Long excludeArticleId) {
        Map<TagType, Set<String>> merged = new HashMap<>();
        for (Map.Entry<Long, Map<TagType, Set<String>>> entry : tagsByArticle.entrySet()) {
            if (entry.getKey().equals(excludeArticleId)) {
                continue;
            }
            for (Map.Entry<TagType, Set<String>> typeEntry : entry.getValue().entrySet()) {
                merged.computeIfAbsent(typeEntry.getKey(), k -> new HashSet<>()).addAll(typeEntry.getValue());
            }
        }
        return merged;
    }

    /**
     * 기사 태그가 클러스터와 맞지 않는지 판단한다.
     *
     * STOCK, SECTOR, TOPIC 중 하나도 겹치지 않으면 mismatch로 간주한다.
     * TOPIC 교집합 검사 시 광범위 TOPIC 블랙리스트(ECONOMY, MARKET 등)는 제외하여,
     * 거의 모든 기사에 붙는 상위 태그가 이상치 판정을 무력화하는 문제를 방지한다.
     */
    private boolean isTagMismatch(Map<TagType, Set<String>> candidateTagsByType,
                                  Map<TagType, Set<String>> clusterTagsByType) {
        if (candidateTagsByType.isEmpty()) {
            return false;
        }

        return !hasOverlap(TagType.STOCK, candidateTagsByType, clusterTagsByType)
                && !hasOverlap(TagType.SECTOR, candidateTagsByType, clusterTagsByType)
                && !hasTopicOverlapExcludingBlacklist(candidateTagsByType, clusterTagsByType);
    }

    /**
     * 특정 TagType에 대해 후보 태그와 클러스터 태그 사이에 교집합이 있는지 검사한다.
     */
    private boolean hasOverlap(TagType type,
                               Map<TagType, Set<String>> candidateTagsByType,
                               Map<TagType, Set<String>> clusterTagsByType) {
        Set<String> candidate = candidateTagsByType.getOrDefault(type, Collections.emptySet());
        Set<String> cluster = clusterTagsByType.getOrDefault(type, Collections.emptySet());
        if (candidate.isEmpty() || cluster.isEmpty()) {
            return false;
        }
        for (String code : candidate) {
            if (cluster.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * TOPIC 교집합을 검사하되, 광범위 블랙리스트 태그를 제외한다.
     *
     * ECONOMY, MARKET, INDUSTRY 같은 태그는 거의 모든 기사에 붙어서 교집합이 항상 존재하는 문제를 유발한다.
     * 이 태그들을 제거한 뒤 남은 TOPIC끼리만 교집합을 검사한다
     */
    private boolean hasTopicOverlapExcludingBlacklist(
            Map<TagType, Set<String>> candidateTagsByType,
            Map<TagType, Set<String>> clusterTagsByType) {
        Set<String> candidateTopics = candidateTagsByType.getOrDefault(TagType.TOPIC, Collections.emptySet());
        Set<String> clusterTopics = clusterTagsByType.getOrDefault(TagType.TOPIC, Collections.emptySet());

        // 블랙리스트 제외
        Set<String> filteredCandidate = candidateTopics.stream()
                .filter(code -> !broadTopicBlacklist.contains(code))
                .collect(Collectors.toSet());
        Set<String> filteredCluster = clusterTopics.stream()
                .filter(code -> !broadTopicBlacklist.contains(code))
                .collect(Collectors.toSet());

        if (filteredCandidate.isEmpty() || filteredCluster.isEmpty()) {
            return false;
        }
        for (String code : filteredCandidate) {
            if (filteredCluster.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 클러스터 태그 분산 체크 (모니터링용)
     *
     * 특정 카테고리가 충분히 지배적이지 않으면 경고 로그 출력
     */
    private void checkCategoryDominance(Long clusterId, List<NewsClusterArticle> mappings,
                                         List<NewsArticleTag> tags) {
        // SECTOR, TOPIC만 대상으로 분석
        // → 클러스터는 산업/주제 단위로 묶이므로 상위 개념(SECTOR, TOPIC)만 사용
        List<NewsArticleTag> categoryTags = tags.stream()
                .filter(t -> t.getTagType() == TagType.SECTOR || t.getTagType() == TagType.TOPIC)
                .toList();

        if (categoryTags.isEmpty()) {
            return;
        }

        // 태그별 distinct 기사 집계
        Map<String, Set<Long>> tagToArticles = categoryTags.stream()
                .collect(Collectors.groupingBy(
                        NewsArticleTag::getTagCode,
                        Collectors.mapping(NewsArticleTag::getNewsArticleId, Collectors.toSet())));

        // 최대 비중 계산
        int maxCount = tagToArticles.values().stream().mapToInt(Set::size).max().orElse(0);
        double dominance = (double) maxCount / mappings.size();

        // 기준보다 낮으면 경고
        if (dominance < CATEGORY_DOMINANCE_THRESHOLD) {
            log.warn("클러스터 category 분산 경고 — clusterId: {}, 최대 비중: {}%",
                    clusterId, String.format("%.1f", dominance * 100));
        }
    }
}
