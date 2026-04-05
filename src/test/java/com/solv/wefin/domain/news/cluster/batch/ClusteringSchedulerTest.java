package com.solv.wefin.domain.news.cluster.batch;

import com.solv.wefin.domain.news.cluster.service.ClusterLifecycleService;
import com.solv.wefin.domain.news.cluster.service.ClusteringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusteringSchedulerTest {

    @InjectMocks
    private ClusteringScheduler clusteringScheduler;

    @Mock
    private ClusteringService clusteringService;

    @Mock
    private ClusterLifecycleService clusterLifecycleService;

    @Test
    @DisplayName("정상 실행 시 비활성화 후 클러스터링을 수행한다")
    void execute_success() {
        // when
        boolean result = clusteringScheduler.execute();

        // then
        assertThat(result).isTrue();
        verify(clusterLifecycleService).deactivateExpiredClusters();
        verify(clusteringService).clusterPendingArticles();
    }

    @Test
    @DisplayName("비활성화 실패 시에도 클러스터링은 계속 진행한다")
    void execute_lifecycleFails_clusteringContinues() {
        // given
        doThrow(new RuntimeException("비활성화 실패"))
                .when(clusterLifecycleService).deactivateExpiredClusters();

        // when
        boolean result = clusteringScheduler.execute();

        // then
        assertThat(result).isTrue();
        verify(clusteringService).clusterPendingArticles();
    }

    @Test
    @DisplayName("중복 실행 시 false를 반환한다")
    void execute_alreadyRunning() throws InterruptedException {
        // given — 첫 번째 실행을 느리게 만듦
        doAnswer(invocation -> {
            Thread.sleep(200);
            return null;
        }).when(clusteringService).clusterPendingArticles();

        // when — 동시에 두 번 실행
        Thread thread1 = new Thread(() -> clusteringScheduler.execute());
        thread1.start();
        Thread.sleep(50); // 첫 번째가 시작된 후

        boolean secondResult = clusteringScheduler.execute();

        thread1.join();

        // then
        assertThat(secondResult).isFalse();
    }
}
