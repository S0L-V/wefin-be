package com.solv.wefin.domain.news.cluster.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 뉴스 클러스터 조회수 랭킹({@code sort=view}) 집계 배치의 마지막 성공 상태 메타
 *
 * 랭킹: 조회수 기준 뉴스 클러스터 순위 피처 ({@code GET /api/news/clusters?sort=view})
 * 테이블 추적 대상: 랭킹 피처를 뒷받침하는
 *                {@link com.solv.wefin.domain.news.cluster.batch.HotClusterAggregationScheduler}
 *                배치의 마지막 성공 실행 스냅샷
 * 단일 row (id=1) 고정 — 배치 성공마다 UPSERT 로 덮어씀. 이력은 Micrometer 메트릭 담당
 * {@code sort=view} 응답에 {@code lastSuccessAt} 노출
 *          → FE 가 랭킹 데이터 지연을 감지하면
 *            "집계 준비 중" 안내 또는 {@code sort=publishedAt} 폴백을 판단할 수 있음
 */
@Entity
@Table(name = "hot_aggregation_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotAggregationMeta {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "last_success_at", nullable = false)
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_window_start", nullable = false)
    private OffsetDateTime lastWindowStart;

    @Column(name = "last_updated_count", nullable = false)
    private int lastUpdatedCount;

    @Column(name = "last_took_ms", nullable = false)
    private int lastTookMs;

    /**
     * 테스트/디버깅 목적의 객체 생성 팩토리
     *
     * 프로덕션 경로에서는 {@code HotAggregationMetaRepository.upsertLatest} 네이티브 쿼리로만 저장되므로
     * 이 메서드는 테스트 fixture 생성과 같은 용도로만 사용한다.
     */
    public static HotAggregationMeta forTest(OffsetDateTime lastSuccessAt,
                                             OffsetDateTime lastWindowStart,
                                             int lastUpdatedCount,
                                             int lastTookMs) {
        HotAggregationMeta meta = new HotAggregationMeta();
        meta.id = SINGLETON_ID;
        meta.lastSuccessAt = lastSuccessAt;
        meta.lastWindowStart = lastWindowStart;
        meta.lastUpdatedCount = lastUpdatedCount;
        meta.lastTookMs = lastTookMs;
        return meta;
    }
}
