package com.solv.wefin.domain.news.cluster.batch;

import com.solv.wefin.domain.news.cluster.repository.HotAggregationMetaRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.config.NewsHotProperties;
import com.solv.wefin.global.config.AdvisoryLockKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 뉴스 클러스터 조회수 랭킹({@code sort=view}) 집계 배치 실행부
 *
 * {@link NewsClusterRepository#refreshRecentViewCounts} 로 ACTIVE 클러스터들의 최근 N시간 고유 뷰어 수를
 * 갱신하고, {@link HotAggregationMetaRepository#upsertLatest} 로 헬스체크 메타를 UPSERT 한다.
 *
 * advisory lock 획득과 DB 쓰기를 같은 트랜잭션에서 처리해 커넥션 경계로 인한 lock leak 을 원천 차단한다.
 * {@code pg_try_advisory_xact_lock} 은 트랜잭션 commit/rollback 시 자동 해제되므로 명시적 unlock 이 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotAggregationRunner {

    private final NewsClusterRepository newsClusterRepository;
    private final HotAggregationMetaRepository metaRepository;
    private final NewsHotProperties newsHotProperties;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 최근 N시간 윈도우 조회수를 ACTIVE 클러스터에 대해 재계산하고 메타 row 를 갱신한다.
     */
    @Transactional
    public void doRefresh() {
        // advisory lock 시도 (트랜잭션 단위). 이미 다른 실행이 있으면 false 반환 (대기하지 않음)
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, AdvisoryLockKeys.HOT_NEWS_AGG);

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[hot-news-agg] result=skipped reason=lock_held_by_other_instance");
            return; // 다른 인스턴스/스레드가 수행 중 → 중복 실행 방지
        }

        long start = System.currentTimeMillis(); // 수행 시간 측정 시작
        OffsetDateTime now = OffsetDateTime.now();
        // 최근 N시간 윈도우 시작 시각 계산
        OffsetDateTime windowStart = now.minusHours(newsHotProperties.windowHours());

        // ACTIVE 클러스터 대상 최근 조회수 재집계
        // 내부적으로 "변경된 row만 UPDATE" (IS DISTINCT FROM)로 write 최소화
        int updated = newsClusterRepository.refreshRecentViewCounts(windowStart);

        int tookMs = (int) (System.currentTimeMillis() - start); // 수행 시간 계산

        // 배치 메타 정보 UPSERT (id=1 고정)
        // → FE에서 last_success_at 기반 fallback 판단에 사용
        metaRepository.upsertLatest(now, windowStart, updated, tookMs);

        // 구조화 로그: 결과/윈도우/갱신건수/소요시간 → metric-like 집계 가능
        log.info("[hot-news-agg] result=success windowStart={} updated={} tookMs={}",
                windowStart, updated, tookMs);
    }
}
