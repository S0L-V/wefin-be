package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.HotAggregationMeta;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주요뉴스 관련 네이티브 SQL 통합 테스트
 *
 * 검증 대상:
 *  {@code UserNewsClusterReadRepository.insertIfAbsent} — UPSERT DO NOTHING 시맨틱 (affected rows)
 *  {@code UserNewsClusterReadRepository.touchReadAtIfStale} — throttle 경계 동작
 *  {@code NewsClusterRepository.refreshRecentViewCounts} — IS DISTINCT FROM 가드 + 윈도우 밖 0 리셋
 *  {@code NewsClusterRepository.incrementUniqueViewerCount} — ACTIVE 가드
 *  {@code HotAggregationMetaRepository.upsertLatest} — id=1 단일 row UPSERT
 */
@SpringBootTest
@ActiveProfiles("test")
class HotNewsAggregationIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("wefin_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @Autowired private NewsClusterRepository newsClusterRepository;
    @Autowired private UserNewsClusterReadRepository userReadRepository;
    @Autowired private HotAggregationMetaRepository metaRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanup() {
        transactionTemplate.executeWithoutResult(tx -> {
            entityManager.createNativeQuery("DELETE FROM user_news_cluster_read").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM news_cluster").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM hot_aggregation_meta").executeUpdate();
        });
    }

    // --- insertIfAbsent ---

    @Test
    @DisplayName("insertIfAbsent — 첫 호출은 1 반환, 동일 (user,cluster) 재호출은 0")
    void insertIfAbsent_upsertSemantics() {
        Long clusterId = seedActiveCluster();
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        int first = transactionTemplate.execute(tx ->
                userReadRepository.insertIfAbsent(userId, clusterId, now));
        int second = transactionTemplate.execute(tx ->
                userReadRepository.insertIfAbsent(userId, clusterId, now.plusSeconds(10)));

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);

        // 실제 row 는 하나만 존재하며 read_at 은 첫 insert 시각 유지 (DO NOTHING 동작)
        Number total = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM user_news_cluster_read WHERE user_id = ? AND news_cluster_id = ?")
                .setParameter(1, userId).setParameter(2, clusterId).getSingleResult();
        assertThat(total.intValue()).isEqualTo(1);
    }

    // --- touchReadAtIfStale ---

    @Test
    @DisplayName("touchReadAtIfStale — threshold 이내 호출은 0 반환 (skip), 이전 값은 유지")
    void touchReadAtIfStale_withinThreshold_skips() {
        Long clusterId = seedActiveCluster();
        UUID userId = UUID.randomUUID();
        OffsetDateTime initial = OffsetDateTime.now().minusSeconds(10);

        transactionTemplate.executeWithoutResult(tx ->
                userReadRepository.insertIfAbsent(userId, clusterId, initial));

        // threshold = now - 60s, initial 이 10s 전이므로 stale 아님
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusSeconds(60);

        int affected = transactionTemplate.execute(tx ->
                userReadRepository.touchReadAtIfStale(userId, clusterId, now, threshold));

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("touchReadAtIfStale — threshold 초과하면 1 반환하고 read_at 갱신")
    void touchReadAtIfStale_beyondThreshold_updates() {
        Long clusterId = seedActiveCluster();
        UUID userId = UUID.randomUUID();
        OffsetDateTime oldTime = OffsetDateTime.now().minusMinutes(10);

        transactionTemplate.executeWithoutResult(tx ->
                userReadRepository.insertIfAbsent(userId, clusterId, oldTime));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusSeconds(60);

        int affected = transactionTemplate.execute(tx ->
                userReadRepository.touchReadAtIfStale(userId, clusterId, now, threshold));

        assertThat(affected).isEqualTo(1);
    }

    // --- incrementUniqueViewerCount ---

    @Test
    @DisplayName("incrementUniqueViewerCount — ACTIVE 클러스터는 1 반환, INACTIVE 는 0 반환하며 값 변경 없음")
    void incrementUniqueViewerCount_activeGuard() {
        Long activeId = seedActiveCluster();
        Long inactiveId = seedInactiveCluster();

        int affectedActive = transactionTemplate.execute(tx ->
                newsClusterRepository.incrementUniqueViewerCount(activeId));
        int affectedInactive = transactionTemplate.execute(tx ->
                newsClusterRepository.incrementUniqueViewerCount(inactiveId));

        assertThat(affectedActive).isEqualTo(1);
        assertThat(affectedInactive).isEqualTo(0);

        Number activeCount = (Number) entityManager.createNativeQuery(
                "SELECT unique_viewer_count FROM news_cluster WHERE news_cluster_id = ?")
                .setParameter(1, activeId).getSingleResult();
        Number inactiveCount = (Number) entityManager.createNativeQuery(
                "SELECT unique_viewer_count FROM news_cluster WHERE news_cluster_id = ?")
                .setParameter(1, inactiveId).getSingleResult();

        assertThat(activeCount.longValue()).isEqualTo(1L);
        assertThat(inactiveCount.longValue()).isEqualTo(0L);
    }

    // --- refreshRecentViewCounts ---

    @Test
    @DisplayName("refreshRecentViewCounts — 변경분만 UPDATE (IS DISTINCT FROM 가드)")
    void refreshRecentViewCounts_updatesOnlyChangedRows() {
        Long c1 = seedActiveCluster();
        Long c2 = seedActiveCluster();

        // c1 에 2건 read, c2 에 0건
        seedReadAt(c1, OffsetDateTime.now().minusMinutes(5));
        seedReadAt(c1, OffsetDateTime.now().minusMinutes(10));

        OffsetDateTime windowStart = OffsetDateTime.now().minusHours(3);

        int updated = transactionTemplate.execute(tx ->
                newsClusterRepository.refreshRecentViewCounts(windowStart));
        // c1 (0→2), c2 (0→0 이지만 IS DISTINCT FROM 가드로 skip) → 1 row 만 UPDATE
        assertThat(updated).isEqualTo(1);

        assertThat(readRecentView(c1)).isEqualTo(2L);
        assertThat(readRecentView(c2)).isEqualTo(0L);

        // 두 번째 호출 — 값 변동 없으면 0 row UPDATE (IS DISTINCT FROM 가드 검증)
        int secondUpdated = transactionTemplate.execute(tx ->
                newsClusterRepository.refreshRecentViewCounts(windowStart));
        assertThat(secondUpdated).isEqualTo(0);
    }

    @Test
    @DisplayName("refreshRecentViewCounts — 윈도우 밖으로 빠진 클러스터는 0으로 리셋")
    void refreshRecentViewCounts_resetsOutOfWindow() {
        Long c = seedActiveCluster();

        // 초기: 윈도우 안에 read 1건 → recent_view_count = 1
        seedReadAt(c, OffsetDateTime.now().minusMinutes(30));
        OffsetDateTime tightWindow = OffsetDateTime.now().minusHours(1);
        transactionTemplate.executeWithoutResult(tx ->
                newsClusterRepository.refreshRecentViewCounts(tightWindow));
        assertThat(readRecentView(c)).isEqualTo(1L);

        // 윈도우를 좁혀서 read 를 경계 밖으로 밀어냄 (윈도우 = 최근 10분)
        OffsetDateTime narrowerWindow = OffsetDateTime.now().minusMinutes(10);
        int updated = transactionTemplate.execute(tx ->
                newsClusterRepository.refreshRecentViewCounts(narrowerWindow));

        assertThat(updated).isEqualTo(1);            // 1→0 으로 변경됐으므로
        assertThat(readRecentView(c)).isEqualTo(0L); // 경계 밖 리셋 검증
    }

    // --- hot_aggregation_meta UPSERT ---

    @Test
    @DisplayName("upsertLatest — 여러 번 호출해도 row 는 id=1 하나만 유지되며 최신 값 반영")
    void upsertLatest_singleRow() {
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);
        OffsetDateTime t2 = OffsetDateTime.now();

        transactionTemplate.executeWithoutResult(tx ->
                metaRepository.upsertLatest(t1, t1.minusHours(3), 5, 100));
        transactionTemplate.executeWithoutResult(tx ->
                metaRepository.upsertLatest(t2, t2.minusHours(3), 42, 200));

        // row 수 = 1
        Number total = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM hot_aggregation_meta").getSingleResult();
        assertThat(total.intValue()).isEqualTo(1);

        // 최신 값 반영
        Optional<HotAggregationMeta> latest = metaRepository.findSingleton();
        assertThat(latest).isPresent();
        assertThat(latest.get().getLastUpdatedCount()).isEqualTo(42);
        assertThat(latest.get().getLastTookMs()).isEqualTo(200);
        // 시각 비교는 초 단위 오차 허용 (DB round-trip / 정밀도 차이)
        assertThat(
                java.time.Duration.between(latest.get().getLastSuccessAt(), t2).abs().getSeconds())
                .isLessThanOrEqualTo(10);
    }

    // --- helpers ---

    private Long seedActiveCluster() {
        return seedCluster(ClusterStatus.ACTIVE);
    }

    private Long seedInactiveCluster() {
        return seedCluster(ClusterStatus.INACTIVE);
    }

    private Long seedCluster(ClusterStatus status) {
        return transactionTemplate.execute(tx -> {
            NewsCluster cluster = NewsCluster.builder()
                    .clusterType(NewsCluster.ClusterType.GENERAL)
                    .centroidVector(new float[]{0.1f})
                    .representativeArticleId(1L)
                    .publishedAt(OffsetDateTime.now())
                    .build();
            ReflectionTestUtils.setField(cluster, "title", "테스트 클러스터");
            ReflectionTestUtils.setField(cluster, "summary", "테스트 요약");
            ReflectionTestUtils.setField(cluster, "status", status);
            ReflectionTestUtils.setField(cluster, "summaryStatus", SummaryStatus.GENERATED);
            newsClusterRepository.save(cluster);
            return cluster.getId();
        });
    }

    private void seedReadAt(Long clusterId, OffsetDateTime readAt) {
        transactionTemplate.executeWithoutResult(tx ->
                userReadRepository.insertIfAbsent(UUID.randomUUID(), clusterId, readAt));
    }

    private long readRecentView(Long clusterId) {
        entityManager.flush();
        entityManager.clear();
        Number n = (Number) entityManager.createNativeQuery(
                "SELECT recent_view_count FROM news_cluster WHERE news_cluster_id = ?")
                .setParameter(1, clusterId).getSingleResult();
        return n.longValue();
    }

}
