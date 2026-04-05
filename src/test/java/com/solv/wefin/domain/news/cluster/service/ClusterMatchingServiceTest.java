package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClusterMatchingServiceTest {

    @InjectMocks
    private ClusterMatchingService clusterMatchingService;

    @Mock
    private NewsClusterArticleRepository clusterArticleRepository;

    @Mock
    private ArticleVectorService articleVectorService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clusterMatchingService, "threshold", 0.80);
        ReflectionTestUtils.setField(clusterMatchingService, "sampleK", 5);
    }

    @Test
    @DisplayName("centroid 유사도가 threshold 이상이면 매칭 성공")
    void findBestMatch_success() {
        // given
        float[] articleVector = {1.0f, 0.0f, 0.0f};
        float[] centroid = {0.9f, 0.1f, 0.0f};

        NewsCluster cluster = NewsCluster.createSingle(centroid, 1L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "id", 1L);

        given(clusterArticleRepository.findByNewsClusterIdOrderByCreatedAtDesc(eq(1L), any()))
                .willReturn(List.of());

        // when
        Optional<ClusterMatchingService.MatchResult> result =
                clusterMatchingService.findBestMatch(articleVector, List.of(cluster));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().cluster()).isEqualTo(cluster);
        assertThat(result.get().similarity()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    @DisplayName("centroid 유사도가 threshold 미만이면 매칭 실패")
    void findBestMatch_belowThreshold() {
        // given
        float[] articleVector = {1.0f, 0.0f, 0.0f};
        float[] centroid = {0.0f, 1.0f, 0.0f}; // 직교 → 유사도 0

        NewsCluster cluster = NewsCluster.createSingle(centroid, 1L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "id", 1L);

        // when
        Optional<ClusterMatchingService.MatchResult> result =
                clusterMatchingService.findBestMatch(articleVector, List.of(cluster));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE 클러스터가 없으면 매칭 실패")
    void findBestMatch_emptyClusters() {
        // given
        float[] articleVector = {1.0f, 0.0f, 0.0f};

        // when
        Optional<ClusterMatchingService.MatchResult> result =
                clusterMatchingService.findBestMatch(articleVector, List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("여러 클러스터 중 가장 유사한 클러스터를 반환한다")
    void findBestMatch_selectsBestCluster() {
        // given
        float[] articleVector = {1.0f, 0.0f, 0.0f};
        float[] centroid1 = {0.85f, 0.15f, 0.0f}; // 유사도 높음
        float[] centroid2 = {0.95f, 0.05f, 0.0f}; // 유사도 더 높음

        NewsCluster cluster1 = NewsCluster.createSingle(centroid1, 1L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster1, "id", 1L);
        NewsCluster cluster2 = NewsCluster.createSingle(centroid2, 2L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster2, "id", 2L);

        given(clusterArticleRepository.findByNewsClusterIdOrderByCreatedAtDesc(any(), any()))
                .willReturn(List.of());

        // when
        Optional<ClusterMatchingService.MatchResult> result =
                clusterMatchingService.findBestMatch(articleVector, List.of(cluster1, cluster2));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().cluster()).isEqualTo(cluster2);
    }
}
