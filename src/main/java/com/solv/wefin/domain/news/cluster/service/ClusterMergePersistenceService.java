package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 클러스터 병합의 DB 작업을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterMergePersistenceService {

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleVectorService articleVectorService;

    /**
     * 두 클러스터를 병합한다.
     *
     * loser 클러스터를 survivor 클러스터로 병합한다.
     * loser의 기사 매핑을 survivor로 이관하되, 이미 존재하는 기사는 중복 추가하지 않는다.
     * 병합 후 survivor의 집계 정보와 centroid를 재계산하고 summary 상태를 STALE로 변경하며,
     * loser는 INACTIVE 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergePair(Long survivorId, Long loserId) {
        NewsCluster survivor = newsClusterRepository.findById(survivorId)
                .orElseThrow(() -> new IllegalStateException("survivor 클러스터 없음: " + survivorId));
        NewsCluster loser = newsClusterRepository.findById(loserId)
                .orElseThrow(() -> new IllegalStateException("loser 클러스터 없음: " + loserId));

        if (loser.getStatus() != ClusterStatus.ACTIVE) {
            return;
        }

        // survivor에 이미 있는 기사 ID (중복 매핑 방어)
        Set<Long> survivorArticleIds = clusterArticleRepository.findByNewsClusterId(survivorId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .collect(Collectors.toSet());

        // loser 매핑을 survivor로 이관 (중복 제외)
        List<NewsClusterArticle> loserMappings = clusterArticleRepository.findByNewsClusterId(loserId);
        int transferredCount = 0;

        for (NewsClusterArticle mapping : loserMappings) {
            clusterArticleRepository.deleteByNewsClusterIdAndNewsArticleId(loserId, mapping.getNewsArticleId());

            if (!survivorArticleIds.contains(mapping.getNewsArticleId())) {
                clusterArticleRepository.save(
                        NewsClusterArticle.create(survivorId, mapping.getNewsArticleId(), 0, false));
                survivorArticleIds.add(mapping.getNewsArticleId());
                transferredCount++;
            }
        }

        // survivor 집계 재계산 (이관 후 전체 매핑 기준)
        int originalSurvivorCount = survivor.getArticleCount();
        List<NewsClusterArticle> allSurvivorMappings = clusterArticleRepository.findByNewsClusterId(survivorId);

        // centroid 재계산 (TODO: N+1 — articleVectorService에 batch 조회 메서드 추가 시 개선)
        float[] newCentroid = recalculateCentroid(allSurvivorMappings);

        // 대표 기사 재선정 — loser에서 이관된 기사 중 더 최신이 있을 수 있으므로 전체에서 재선정
        List<Long> allArticleIds = allSurvivorMappings.stream()
                .map(NewsClusterArticle::getNewsArticleId).toList();
        NewsArticle representative = newsArticleRepository.findAllById(allArticleIds).stream()
                .filter(a -> a.getPublishedAt() != null)
                .max((a, b) -> a.getPublishedAt().compareTo(b.getPublishedAt()))
                .orElseGet(() -> newsArticleRepository.findAllById(allArticleIds).stream().findAny().orElse(null));

        survivor.recalculateAggregates(
                allSurvivorMappings.size(), newCentroid,
                representative != null ? representative.getId() : survivor.getRepresentativeArticleId(),
                representative != null ? representative.getPublishedAt() : survivor.getPublishedAt(),
                representative != null ? representative.getThumbnailUrl() : survivor.getThumbnailUrl());

        survivor.markSummaryStale();
        loser.deactivate();

        log.info("클러스터 병합 — survivor: {} ({}건), loser: {} ({}건→INACTIVE), 이관: {}건, 합산: {}건",
                survivorId, originalSurvivorCount,
                loserId, loserMappings.size(),
                transferredCount, allSurvivorMappings.size());
    }

    private float[] recalculateCentroid(List<NewsClusterArticle> mappings) {
        List<float[]> vectors = mappings.stream()
                .map(m -> articleVectorService.calculateRepresentativeVector(m.getNewsArticleId()))
                .filter(v -> v != null)
                .toList();

        if (vectors.isEmpty()) {
            return null;
        }

        int dimension = vectors.get(0).length;
        float[] sum = new float[dimension];
        for (float[] v : vectors) {
            for (int i = 0; i < dimension; i++) {
                sum[i] += v[i];
            }
        }
        float count = vectors.size();
        float[] centroid = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            centroid[i] = sum[i] / count;
        }
        return centroid;
    }
}
