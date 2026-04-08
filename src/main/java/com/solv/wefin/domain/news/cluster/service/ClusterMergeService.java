package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유사한 ACTIVE 클러스터를 병합하여 중복을 해소한다.
 *
 * 같은 주제의 기사가 별개 클러스터로 분산되는 문제를 해결한다.
 * centroid 유사도가 임계값 이상인 쌍을 찾아 기사 수가 많은 쪽(survivor)에
 * 작은 쪽의 매핑을 이관하고, 작은 쪽은 INACTIVE 처리한다.
 */
@Slf4j
@Service
public class ClusterMergeService {

    private final NewsClusterRepository newsClusterRepository;
    private final ClusterMatchingService clusterMatchingService;
    private final ClusterMergePersistenceService mergePersistenceService;
    private final double mergeThreshold;

    public ClusterMergeService(
            NewsClusterRepository newsClusterRepository,
            ClusterMatchingService clusterMatchingService,
            ClusterMergePersistenceService mergePersistenceService,
            @Value("${clustering.merge.threshold:0.85}") double mergeThreshold) {
        this.newsClusterRepository = newsClusterRepository;
        this.clusterMatchingService = clusterMatchingService;
        this.mergePersistenceService = mergePersistenceService;
        this.mergeThreshold = mergeThreshold;
    }

    /**
     * ACTIVE 클러스터 중 유사한 쌍을 찾아 병합한다.
     *
     * @return 병합된 쌍 수
     */
    public int mergeActiveClusters() {
        List<NewsCluster> activeClusters = newsClusterRepository.findByStatus(ClusterStatus.ACTIVE);
        log.info("클러스터 병합 시작 — ACTIVE 클러스터: {}개", activeClusters.size());

        if (activeClusters.size() < 2) {
            return 0;
        }

        List<NewsCluster> candidates = activeClusters.stream()
                .filter(c -> c.getCentroidVector() != null)
                .toList();

        long startTime = System.currentTimeMillis();
        List<MergePair> pairs = findMergePairs(candidates);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("병합 후보 탐색 완료 — candidates: {}개, 후보 쌍: {}개, 소요: {}ms (threshold: {})",
                candidates.size(), pairs.size(), elapsed, mergeThreshold);

        int mergedCount = 0;
        for (MergePair pair : pairs) {
            try {
                boolean merged = mergePersistenceService.mergePair(pair.survivorId, pair.loserId);
                if (merged) {
                    mergedCount++;
                }
            } catch (Exception e) {
                log.warn("클러스터 병합 실패 — survivor: {}, loser: {}, error: {}",
                        pair.survivorId, pair.loserId, e.getMessage());
            }
        }

        log.info("클러스터 병합 완료 — 병합: {}쌍", mergedCount);
        return mergedCount;
    }

    /**
     * centroid 유사도가 임계값 이상인 병합 후보 쌍을 찾는다.
     *
     * 한 클러스터가 여러 쌍에 등장할 수 있으므로, 이미 선택된 클러스터는
     * 후속 쌍에서 제외한다 (greedy: 유사도 높은 쌍 우선)
     */
    private List<MergePair> findMergePairs(List<NewsCluster> candidates) {
        List<MergePair> allPairs = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                NewsCluster a = candidates.get(i);
                NewsCluster b = candidates.get(j);

                double similarity = clusterMatchingService.cosineSimilarity(
                        a.getCentroidVector(), b.getCentroidVector());

                if (similarity >= mergeThreshold) {
                    // 기사 수가 많은 쪽이 survivor
                    boolean aIsSurvivor = a.getArticleCount() >= b.getArticleCount();
                    Long survivorId = aIsSurvivor ? a.getId() : b.getId();
                    Long loserId = aIsSurvivor ? b.getId() : a.getId();
                    allPairs.add(new MergePair(survivorId, loserId, similarity));
                }
            }
        }

        // 유사도 높은 순 정렬 → greedy 선택
        allPairs.sort((p1, p2) -> Double.compare(p2.similarity, p1.similarity));

        List<MergePair> selected = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();

        for (MergePair pair : allPairs) {
            if (!usedIds.contains(pair.survivorId) && !usedIds.contains(pair.loserId)) {
                selected.add(pair);
                usedIds.add(pair.survivorId);
                usedIds.add(pair.loserId);
            }
        }

        return selected;
    }

    private record MergePair(Long survivorId, Long loserId, double similarity) {}
}
