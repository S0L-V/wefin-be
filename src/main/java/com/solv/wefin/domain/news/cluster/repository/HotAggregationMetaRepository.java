package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.HotAggregationMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface HotAggregationMetaRepository extends JpaRepository<HotAggregationMeta, Short> {

    /**
     * 뉴스 클러스터 조회수 랭킹 배치의 성공 이력을 단일 row(id=1) 에 UPSERT 한다.
     *
     * 이력 보관은 이 테이블의 책임이 아니며(tookMs 추이는 Micrometer 메트릭 담당),
     * FE 폴백 판단에 필요한 "마지막 성공 시각"만 항상 최신 값으로 유지한다.
     *
     * @param lastSuccessAt   이번 배치가 성공한 시각
     * @param lastWindowStart 이번 집계에 사용한 윈도우 시작 시각
     * @param lastUpdatedCount 이번 배치가 실제로 UPDATE한 클러스터 수
     * @param lastTookMs      이번 배치 수행 시간(ms)
     * @return UPSERT된 row 수 (항상 1)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO hot_aggregation_meta
                (id, last_success_at, last_window_start, last_updated_count, last_took_ms)
            VALUES (1, :lastSuccessAt, :lastWindowStart, :lastUpdatedCount, :lastTookMs)
            ON CONFLICT (id) DO UPDATE SET
                last_success_at    = EXCLUDED.last_success_at,
                last_window_start  = EXCLUDED.last_window_start,
                last_updated_count = EXCLUDED.last_updated_count,
                last_took_ms       = EXCLUDED.last_took_ms
            """, nativeQuery = true)
    int upsertLatest(@Param("lastSuccessAt") OffsetDateTime lastSuccessAt,
                     @Param("lastWindowStart") OffsetDateTime lastWindowStart,
                     @Param("lastUpdatedCount") int lastUpdatedCount,
                     @Param("lastTookMs") int lastTookMs);

    /**
     * 단일 row(id=1) 를 조회한다. 최초 배포 후 첫 배치 이전에는 {@code Optional.empty()}.
     */
    default Optional<HotAggregationMeta> findSingleton() {
        return findById(HotAggregationMeta.SINGLETON_ID);
    }
}
