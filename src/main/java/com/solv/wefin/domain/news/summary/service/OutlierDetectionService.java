package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.service.ArticleVectorService;
import com.solv.wefin.domain.news.cluster.service.ClusterMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요약 직전 이상치 제거 서비스
 *
 * centroid 유사도 하위 + 태그 불일치 기사를 클러스터 매핑에서 제거하고,
 * 클러스터 집계 상태(articleCount, centroid)를 재계산한다.
 * 제거된 기사는 다음 클러스터링 배치에서 미배정 기사로 자동 수거된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutlierDetectionService {

    // centroid와의 cosine similarity 기준
    // → 0.7 미만이면 해당 클러스터와 의미적으로 어긋난 기사로 판단 (이상치 후보)
    private static final double OUTLIER_SIMILARITY_THRESHOLD = 0.70;

    // 클러스터 내 특정 카테고리 태그의 최대 비중 기준
    // → 0.6 미만이면 주제가 분산된 클러스터로 판단 (경고 로그용, 제거 기준 아님)
    private static final double CATEGORY_DOMINANCE_THRESHOLD = 0.60;

    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleTagRepository articleTagRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleVectorService articleVectorService;
    private final ClusterMatchingService clusterMatchingService;

    /**
     * 클러스터에서 이상치 기사를 제거하고 집계 상태를 갱신한다.
     * 제거된 기사는 다음 클러스터링 배치에서 미배정 기사로 재수거된다.
     *
     * @param cluster 대상 클러스터
     * @return 제거된 기사 수
     */
    @Transactional
    public int removeOutliers(NewsCluster cluster) {
        // 1. 클러스터에 속한 기사-매핑 조회
        List<NewsClusterArticle> mappings = clusterArticleRepository.findByNewsClusterId(cluster.getId());

        // 기사 1개 이하 → 이상치 판단 의미 없음
        if (mappings.size() <= 1) {
            return 0;
        }

        // 2. centroid(클러스터 중심 벡터) 조회
        float[] centroid = cluster.getCentroidVector();

        // centroid 없으면 유사도 계산 불가 → 종료
        if (centroid == null) {
            return 0;
        }

        // 3. 클러스터 태그 분산 체크 (경고 로그용, 제거 로직과는 별개)
        checkCategoryDominance(cluster.getId(), mappings);

        // 4. 유사도 + 태그 기준으로 이상치 기사 탐색
        List<NewsClusterArticle> outliers = findOutliers(mappings, centroid);


        // 이상치 없으면 종료
        if (outliers.isEmpty()) {
            return 0;
        }

        // 5. 이상치 기사 ID 수집 (이후 집계 재계산에서 사용)
        Set<Long> outlierArticleIds = outliers.stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .collect(Collectors.toSet());

        // 6. 클러스터-기사 매핑 삭제 (클러스터에서 제거)
        for (NewsClusterArticle outlier : outliers) {
            clusterArticleRepository.deleteByNewsClusterIdAndNewsArticleId(
                    cluster.getId(), outlier.getNewsArticleId());
            log.info("이상치 제거 — articleId: {}, clusterId: {}", outlier.getNewsArticleId(), cluster.getId());
        }

        // 7. 남은 기사 기준으로 클러스터 상태 재계산
        recalculateClusterState(cluster, mappings, outlierArticleIds);

        // 8. 처리 결과 로그
        log.info("이상치 제거 완료 — clusterId: {}, 제거: {}건, 남은 기사: {}건",
                cluster.getId(), outliers.size(), mappings.size() - outliers.size());

        // 9. 제거된 기사 수 반환
        return outliers.size();
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

        // 남은 기사 없으면 클러스터 초기화 (요약 단계에서 FAILED 처리)
        if (remainingArticleIds.isEmpty()) {
            cluster.recalculateAfterOutlierRemoval(0, null, null, null, null);
            return;
        }

        // 2. centroid 재계산 (남은 기사 벡터 평균)
        List<float[]> remainingVectors = remainingArticleIds.stream()
                .map(articleVectorService::calculateRepresentativeVector)
                .filter(v -> v != null)
                .toList();

        float[] newCentroid = remainingVectors.isEmpty() ? null : averageVectors(remainingVectors);

        // 3. 대표 기사 재선정 (가장 최신 기사)
        var latestArticle = newsArticleRepository.findAllById(remainingArticleIds).stream()
                .filter(a -> a.getPublishedAt() != null)
                .max((a, b) -> a.getPublishedAt().compareTo(b.getPublishedAt()))
                .orElse(null);

        // 4. 클러스터 상태 업데이트
        cluster.recalculateAfterOutlierRemoval(
                remainingArticleIds.size(),
                newCentroid,
                latestArticle != null ? latestArticle.getId() : null,
                latestArticle != null ? latestArticle.getPublishedAt() : null,
                latestArticle != null ? latestArticle.getThumbnailUrl() : null
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
    private List<NewsClusterArticle> findOutliers(List<NewsClusterArticle> mappings, float[] centroid) {
        // 클러스터 전체 기사 ID
        List<Long> articleIds = mappings.stream().map(NewsClusterArticle::getNewsArticleId).toList();

        // 모든 기사 태그 조회 (한 번에 조회해서 성능 최적화)
        List<NewsArticleTag> allTags = articleTagRepository.findByNewsArticleIdIn(articleIds);

        // 클러스터 태그를 타입별(STOCK, SECTOR, TOPIC)로 그룹핑
        Map<TagType, List<String>> clusterTagsByType = allTags.stream()
                .collect(Collectors.groupingBy(
                        NewsArticleTag::getTagType,
                        Collectors.mapping(NewsArticleTag::getTagCode, Collectors.toList())));

        List<NewsClusterArticle> outliers = new ArrayList<>();

        for (NewsClusterArticle mapping : mappings) {
            // 기사 벡터 계산
            float[] vector = articleVectorService.calculateRepresentativeVector(mapping.getNewsArticleId());
            if (vector == null) {
                continue;
            }

            // centroid와 유사도 계산
            double similarity = clusterMatchingService.cosineSimilarity(vector, centroid);

            // 1차 필터: 유사도 낮음
            if (similarity < OUTLIER_SIMILARITY_THRESHOLD) {
                // 해당 기사 태그 추출
                List<NewsArticleTag> articleTags = allTags.stream()
                        .filter(t -> t.getNewsArticleId().equals(mapping.getNewsArticleId()))
                        .toList();

                // 2차 필터: 태그 불일치
                if (isTagMismatch(articleTags, clusterTagsByType)) {
                    outliers.add(mapping);
                }
            }
        }

        return outliers;
    }

    /**
     * 기사 태그가 클러스터와 맞지 않는지 판단
     *
     * STOCK, SECTOR, TOPIC 중 하나도 겹치지 않으면 → mismatch
     */
    private boolean isTagMismatch(List<NewsArticleTag> articleTags,
                                  Map<TagType, List<String>> clusterTagsByType) {
        if (articleTags.isEmpty()) {
            return false;
        }

        // 클러스터 태그 목록
        List<String> clusterStocks = clusterTagsByType.getOrDefault(TagType.STOCK, List.of());
        List<String> clusterSectors = clusterTagsByType.getOrDefault(TagType.SECTOR, List.of());
        List<String> clusterTopics = clusterTagsByType.getOrDefault(TagType.TOPIC, List.of());

        // 각각 교집합 체크
        boolean stockOverlap = articleTags.stream()
                .filter(t -> t.getTagType() == TagType.STOCK)
                .anyMatch(t -> clusterStocks.contains(t.getTagCode()));

        boolean sectorOverlap = articleTags.stream()
                .filter(t -> t.getTagType() == TagType.SECTOR)
                .anyMatch(t -> clusterSectors.contains(t.getTagCode()));

        boolean topicOverlap = articleTags.stream()
                .filter(t -> t.getTagType() == TagType.TOPIC)
                .anyMatch(t -> clusterTopics.contains(t.getTagCode()));

        return !stockOverlap && !sectorOverlap && !topicOverlap;
    }

    /**
     * 클러스터 태그 분산 체크 (모니터링용)
     *
     * 특정 카테고리가 충분히 지배적이지 않으면 경고 로그 출력
     */
    private void checkCategoryDominance(Long clusterId, List<NewsClusterArticle> mappings) {
        List<Long> articleIds = mappings.stream().map(NewsClusterArticle::getNewsArticleId).toList();
        List<NewsArticleTag> tags = articleTagRepository.findByNewsArticleIdIn(articleIds);

        // SECTOR, TOPIC만 대상으로 분석
        // → 클러스터는 산업/주제 단위로 묶이므로 상위 개념(SECTOR, TOPIC)만 사용
        List<NewsArticleTag> categoryTags = tags.stream()
                .filter(t -> t.getTagType() == TagType.SECTOR || t.getTagType() == TagType.TOPIC)
                .toList();

        if (categoryTags.isEmpty()) {
            return;
        }

        // 태그별 개수 집계
        Map<String, Long> tagCounts = categoryTags.stream()
                .collect(Collectors.groupingBy(NewsArticleTag::getTagCode, Collectors.counting()));

        // 최대 비중 계산
        long maxCount = tagCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double dominance = (double) maxCount / categoryTags.size();

        // 기준보다 낮으면 경고
        if (dominance < CATEGORY_DOMINANCE_THRESHOLD) {
            log.warn("클러스터 category 분산 경고 — clusterId: {}, 최대 비중: {}%",
                    clusterId, String.format("%.1f", dominance * 100));
        }
    }
}
