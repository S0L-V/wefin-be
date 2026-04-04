package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클러스터 매칭 서비스
 *
 * 새 기사의 대표 벡터를 기존 ACTIVE 클러스터의 centroid와 비교하여
 * 가장 유사한 클러스터를 찾는다. centroid 매칭 후 상위 K건 샘플링 검증을 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterMatchingService {

    private final NewsClusterArticleRepository clusterArticleRepository;
    private final ArticleVectorService articleVectorService;

    @Value("${clustering.threshold:0.80}")
    private double threshold; // 유사도 기준

    @Value("${clustering.sample-k:5}")
    private int sampleK; // 유사도 비교 시 샘플링할 클러스터 개수

    /**
     * 배치 내에서 기사 대표 벡터를 캐시한다.
     */
    private final Map<Long, float[]> vectorCache = new ConcurrentHashMap<>();

    /**
     * 배치 시작 시 캐시를 초기화한다.
     */
    public void clearCache() {
        vectorCache.clear();
    }

    /**
     * 기존 ACTIVE 클러스터 중 가장 유사한 클러스터를 찾는다.
     *
     * @param articleVector  새 기사의 대표 벡터
     * @param activeClusters 현재 ACTIVE 상태 클러스터 목록
     * @return 매칭된 클러스터와 유사도. 매칭 실패 시 empty
     */
    public Optional<MatchResult> findBestMatch(float[] articleVector, List<NewsCluster> activeClusters) {
        if (activeClusters.isEmpty()) {
            return Optional.empty();
        }

        NewsCluster bestCluster = null;
        double bestSimilarity = 0.0;

        for (NewsCluster cluster : activeClusters) {
            float[] centroid = cluster.getCentroidVector();
            if (centroid == null) {
                continue;
            }

            double similarity = cosineSimilarity(articleVector, centroid);
            if (similarity >= threshold && similarity > bestSimilarity) {
                bestCluster = cluster;
                bestSimilarity = similarity;
            }
        }

        if (bestCluster == null) {
            return Optional.empty();
        }

        // 상위 K건 샘플링 검증
        if (!validateWithSampling(articleVector, bestCluster)) {
            log.debug("상위 K건 샘플링 검증 실패 - clusterId: {}", bestCluster.getId());
            return Optional.empty();
        }

        return Optional.of(new MatchResult(bestCluster, bestSimilarity));
    }

    /**
     * 상위 K건 샘플링 검증
     *
     * 클러스터 소속 기사 중 centroid에 가장 가까운 상위 K건과 새 기사의 유사도를 비교한다.
     */
    private boolean validateWithSampling(float[] articleVector, NewsCluster cluster) {
        // 최신순 정렬된 매핑 조회
        List<NewsClusterArticle> mappings = clusterArticleRepository
                .findByNewsClusterIdOrderByCreatedAtDesc(cluster.getId());

        if (mappings.isEmpty()) {
            return true;
        }

        // cluster size ≤ K면 전량, 초과면 최근 K건만 (벡터 조회 수 제한)
        List<NewsClusterArticle> sample = mappings.size() <= sampleK
                ? mappings
                : mappings.subList(0, sampleK);

        List<float[]> sampleVectors = sample.stream()
                .map(m -> getCachedVector(m.getNewsArticleId()))
                .filter(v -> v != null)
                .toList();

        if (sampleVectors.isEmpty()) {
            return true;
        }

        // K건 중 절반 이상(≥50%, 올림)이 threshold 이상이면 통과
        long passCount = sampleVectors.stream()
                .filter(v -> cosineSimilarity(articleVector, v) >= threshold)
                .count();

        int requiredCount = (sampleVectors.size() + 1) / 2; // 올림: size=4→2, size=5→3
        return passCount >= requiredCount;
    }

    /**
     * 기사 대표 벡터를 캐시에서 조회하거나, 없으면 계산하여 캐시에 저장한다.
     */
    private float[] getCachedVector(Long articleId) {
        return vectorCache.computeIfAbsent(articleId, articleVectorService::calculateRepresentativeVector);
    }

    /**
     * 두 벡터 간 cosine similarity를 계산한다.
     * cosine similarity = (A · B) / (|A| * |B|)
     *
     * @return -1.0 ~ 1.0 사이의 유사도 값
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0; // 두 벡터의 내적 (A · B)
        double normA = 0.0;      // 벡터 A의 크기 |A|
        double normB = 0.0;      // 벡터 B의 크기 |B|

        // 각 차원을 순회하며 내적과 벡터 크기 계산
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        // 분모: |A| * |B|
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);

        // 벡터 크기가 0이면 유사도 정의 불가
        if (denominator == 0.0) {
            return 0.0;
        }

        // 최종 cosine similarity 계산
        return dotProduct / denominator;
    }

    /**
     * 클러스터 매칭 결과
     */
    public record MatchResult(NewsCluster cluster, double similarity) {
    }
}
