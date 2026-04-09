package com.solv.wefin.domain.news.cluster.entity;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class NewsClusterTest {

    @Test
    @DisplayName("GENERATED 상태에서 markSummaryStale 호출 시 STALE로 전환된다")
    void markSummaryStale_fromGenerated() {
        NewsCluster cluster = createCluster(SummaryStatus.GENERATED);

        cluster.markSummaryStale();

        assertThat(cluster.getSummaryStatus()).isEqualTo(SummaryStatus.STALE);
    }

    @Test
    @DisplayName("PENDING 상태에서 markSummaryStale 호출 시 상태가 유지된다")
    void markSummaryStale_fromPending_noChange() {
        NewsCluster cluster = createCluster(SummaryStatus.PENDING);

        cluster.markSummaryStale();

        assertThat(cluster.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
    }

    @Test
    @DisplayName("FAILED 상태에서 markSummaryStale 호출 시 상태가 유지된다")
    void markSummaryStale_fromFailed_noChange() {
        NewsCluster cluster = createCluster(SummaryStatus.FAILED);

        cluster.markSummaryStale();

        assertThat(cluster.getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    }

    @Test
    @DisplayName("STALE 상태에서 markSummaryStale 호출 시 상태가 유지된다")
    void markSummaryStale_fromStale_noChange() {
        NewsCluster cluster = createCluster(SummaryStatus.STALE);

        cluster.markSummaryStale();

        assertThat(cluster.getSummaryStatus()).isEqualTo(SummaryStatus.STALE);
    }

    private NewsCluster createCluster(SummaryStatus summaryStatus) {
        NewsCluster cluster = NewsCluster.builder()
                .clusterType(NewsCluster.ClusterType.GENERAL)
                .centroidVector(new float[]{1.0f})
                .build();
        ReflectionTestUtils.setField(cluster, "summaryStatus", summaryStatus);
        return cluster;
    }
}
