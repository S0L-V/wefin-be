package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserNewsClusterReadRepository extends JpaRepository<UserNewsClusterRead, Long> {

    boolean existsByUserIdAndNewsClusterId(UUID userId, Long newsClusterId);

    List<UserNewsClusterRead> findByUserIdAndNewsClusterIdIn(UUID userId, List<Long> newsClusterIds);

    /**
     * 사용자-클러스터 조회 이력을 중복 없이 원자적으로 기록한다.
     *
     * @return 1: 신규 INSERT (최초 조회)
     *         0: 이미 존재하여 INSERT 생략 (중복 조회)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_news_cluster_read (user_id, news_cluster_id, read_at)
            VALUES (:userId, :clusterId, :readAt)
            ON CONFLICT (user_id, news_cluster_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId,
                       @Param("clusterId") Long clusterId,
                       @Param("readAt") OffsetDateTime readAt);

    /**
     * 재방문 시 {@code read_at} 을 최신 시각으로 갱신하되, 이미 최근에 갱신된 경우(stale 기준 미만) skip 한다.
     *
     * UPSERT 기반 "최근 N시간 내 본 고유 유저 수" 지표를 유지하려면 재방문에도 {@code read_at} 이 갱신돼야 한다.
     * 다만 동일 유저의 짧은 간격 재호출(FE 버그/더블클릭)까지 매번 UPDATE 하면 read_at 인덱스 때문에
     * HOT update 가 깨져 bloat 이 커지므로, 마지막 갱신이 {@code staleThreshold} 보다 이전일 때만 UPDATE 한다.
     *
     * @param staleThreshold 이 시각보다 read_at 이 이전이면 UPDATE 대상 (보통 {@code readAt - throttle})
     * @return 갱신되었으면 1, 스로틀로 인해 skip 되었으면 0 (정상 동작)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE user_news_cluster_read
            SET read_at = :readAt
            WHERE user_id = :userId
              AND news_cluster_id = :clusterId
              AND read_at < :staleThreshold
            """, nativeQuery = true)
    int touchReadAtIfStale(@Param("userId") UUID userId,
                           @Param("clusterId") Long clusterId,
                           @Param("readAt") OffsetDateTime readAt,
                           @Param("staleThreshold") OffsetDateTime staleThreshold);
}
