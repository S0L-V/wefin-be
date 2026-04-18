package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClusterLifecycleServiceTest {

    @InjectMocks
    private ClusterLifecycleService clusterLifecycleService;

    @Mock
    private NewsClusterRepository newsClusterRepository;

    @Test
    @DisplayName("만료 대상이 있으면 INACTIVE로 전환한다")
    void deactivateExpiredClusters_success() {
        // given
        float[] vector = {1.0f, 0.0f, 0.0f};
        NewsCluster cluster = NewsCluster.createSingle(vector, 1L, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "id", 1L);

        given(newsClusterRepository.findByStatusAndUpdatedAtBefore(eq(ClusterStatus.ACTIVE), any()))
                .willReturn(List.of(cluster));

        // when
        clusterLifecycleService.deactivateExpiredClusters();

        // then
        assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.INACTIVE);
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무것도 하지 않는다")
    void deactivateExpiredClusters_noExpired() {
        // given
        given(newsClusterRepository.findByStatusAndUpdatedAtBefore(eq(ClusterStatus.ACTIVE), any()))
                .willReturn(List.of());

        // when
        clusterLifecycleService.deactivateExpiredClusters();

        // then
        verify(newsClusterRepository, never()).saveAll(any());
    }
}
