package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClusterMergeServiceTest {

    @Mock
    private NewsClusterRepository newsClusterRepository;
    @Mock
    private ClusterMatchingService clusterMatchingService;
    @Mock
    private ClusterMergePersistenceService mergePersistenceService;

    private ClusterMergeService clusterMergeService;

    @BeforeEach
    void setUp() {
        clusterMergeService = new ClusterMergeService(
                newsClusterRepository, clusterMatchingService, mergePersistenceService, 0.85);
    }

    @Test
    @DisplayName("유사도 0.85 이상인 클러스터 쌍을 병합한다")
    void mergeActiveClusters_mergeSimilarPair() {
        NewsCluster big = createCluster(1L, 5, new float[]{1.0f, 0.0f});
        NewsCluster small = createCluster(2L, 2, new float[]{0.99f, 0.01f});

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(big, small));
        given(clusterMatchingService.cosineSimilarity(big.getCentroidVector(), small.getCentroidVector()))
                .willReturn(0.90);

        int merged = clusterMergeService.mergeActiveClusters();

        assertThat(merged).isEqualTo(1);
        verify(mergePersistenceService).mergePair(1L, 2L); // big=survivor, small=loser
    }

    @Test
    @DisplayName("유사도가 임계값 미만이면 병합하지 않는다")
    void mergeActiveClusters_belowThreshold_noMerge() {
        NewsCluster a = createCluster(1L, 3, new float[]{1.0f, 0.0f});
        NewsCluster b = createCluster(2L, 3, new float[]{0.0f, 1.0f});

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(a, b));
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), b.getCentroidVector()))
                .willReturn(0.50);

        int merged = clusterMergeService.mergeActiveClusters();

        assertThat(merged).isZero();
        verify(mergePersistenceService, never()).mergePair(anyLong(), anyLong());
    }

    @Test
    @DisplayName("ACTIVE 클러스터가 1개 이하면 병합하지 않는다")
    void mergeActiveClusters_singleCluster_noMerge() {
        NewsCluster single = createCluster(1L, 3, new float[]{1.0f});
        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(single));

        int merged = clusterMergeService.mergeActiveClusters();

        assertThat(merged).isZero();
    }

    @Test
    @DisplayName("centroid가 없는 클러스터는 비교 대상에서 제외한다")
    void mergeActiveClusters_nullCentroid_excluded() {
        NewsCluster withCentroid = createCluster(1L, 3, new float[]{1.0f});
        NewsCluster noCentroid = createCluster(2L, 3, null);

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE))
                .willReturn(List.of(withCentroid, noCentroid));

        int merged = clusterMergeService.mergeActiveClusters();

        assertThat(merged).isZero();
        verify(mergePersistenceService, never()).mergePair(anyLong(), anyLong());
    }

    @Test
    @DisplayName("greedy 선택 — 한 클러스터는 한 쌍에만 사용된다")
    void mergeActiveClusters_greedy_noDuplicateUse() {
        NewsCluster a = createCluster(1L, 5, new float[]{1.0f, 0.0f});
        NewsCluster b = createCluster(2L, 3, new float[]{0.99f, 0.01f});
        NewsCluster c = createCluster(3L, 2, new float[]{0.98f, 0.02f});

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(a, b, c));
        // a-b: 0.95, a-c: 0.90, b-c: 0.88 → 모두 임계값 이상
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), b.getCentroidVector()))
                .willReturn(0.95);
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), c.getCentroidVector()))
                .willReturn(0.90);
        given(clusterMatchingService.cosineSimilarity(b.getCentroidVector(), c.getCentroidVector()))
                .willReturn(0.88);

        int merged = clusterMergeService.mergeActiveClusters();

        // a-b가 유사도 최고라 먼저 선택 → a,b 사용됨 → c는 남은 쌍에서 a,b 모두 사용됐으므로 선택 안 됨
        assertThat(merged).isEqualTo(1);
        verify(mergePersistenceService).mergePair(1L, 2L);
    }

    @Test
    @DisplayName("기사 수가 같으면 앞 클러스터가 survivor가 된다")
    void mergeActiveClusters_sameCount_firstIsSurvivor() {
        NewsCluster a = createCluster(1L, 3, new float[]{1.0f, 0.0f});
        NewsCluster b = createCluster(2L, 3, new float[]{0.99f, 0.01f});

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(a, b));
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), b.getCentroidVector()))
                .willReturn(0.90);

        clusterMergeService.mergeActiveClusters();

        verify(mergePersistenceService).mergePair(1L, 2L); // a가 survivor (같은 수면 >= 조건)
    }

    @Test
    @DisplayName("한 쌍 병합 실패해도 나머지는 계속 진행한다")
    void mergeActiveClusters_partialFailure() {
        NewsCluster a = createCluster(1L, 5, new float[]{1.0f, 0.0f});
        NewsCluster b = createCluster(2L, 2, new float[]{0.99f, 0.01f});
        NewsCluster c = createCluster(3L, 4, new float[]{0.0f, 1.0f});
        NewsCluster d = createCluster(4L, 1, new float[]{0.01f, 0.99f});

        given(newsClusterRepository.findByStatus(ClusterStatus.ACTIVE)).willReturn(List.of(a, b, c, d));
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), b.getCentroidVector()))
                .willReturn(0.95);
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), c.getCentroidVector()))
                .willReturn(0.10);
        given(clusterMatchingService.cosineSimilarity(a.getCentroidVector(), d.getCentroidVector()))
                .willReturn(0.10);
        given(clusterMatchingService.cosineSimilarity(b.getCentroidVector(), c.getCentroidVector()))
                .willReturn(0.10);
        given(clusterMatchingService.cosineSimilarity(b.getCentroidVector(), d.getCentroidVector()))
                .willReturn(0.10);
        given(clusterMatchingService.cosineSimilarity(c.getCentroidVector(), d.getCentroidVector()))
                .willReturn(0.92);

        // a-b 병합은 실패, c-d 병합은 성공
        org.mockito.Mockito.doThrow(new RuntimeException("DB error")).when(mergePersistenceService).mergePair(1L, 2L);

        int merged = clusterMergeService.mergeActiveClusters();

        assertThat(merged).isEqualTo(1); // c-d만 성공
        verify(mergePersistenceService).mergePair(1L, 2L); // 시도는 했음 (실패)
        verify(mergePersistenceService).mergePair(3L, 4L); // 성공
    }

    private NewsCluster createCluster(long id, int articleCount, float[] centroid) {
        NewsCluster cluster = NewsCluster.builder()
                .clusterType(NewsCluster.ClusterType.GENERAL)
                .centroidVector(centroid)
                .build();
        ReflectionTestUtils.setField(cluster, "id", id);
        ReflectionTestUtils.setField(cluster, "articleCount", articleCount);
        ReflectionTestUtils.setField(cluster, "status", ClusterStatus.ACTIVE);
        return cluster;
    }
}
