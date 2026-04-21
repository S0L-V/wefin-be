package com.solv.wefin.domain.news.cluster.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 뉴스 클러스터 조회수 랭킹({@code sort=view}) 집계 배치 스케줄러
 *
 * 호출 트리거와 스케줄 정책(주기, initialDelay)만 담당.
 * 실제 집계와 advisory lock 은 {@link HotAggregationRunner} 가 같은 트랜잭션에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotClusterAggregationScheduler {

    private final HotAggregationRunner runner;

    @Scheduled(
            fixedDelayString = "${news.hot.aggregation-interval-seconds:300}",
            initialDelayString = "${news.hot.initial-delay-seconds:30}",
            timeUnit = TimeUnit.SECONDS
    )
    public void refresh() {
        try {
            runner.doRefresh();
        } catch (Exception e) {
            // 예외 swallow: 다음 주기에 자연 재시도. 로그 수집기에서 result=error 로 집계 가능.
            log.error("[hot-news-agg] result=error message={}", e.getMessage(), e);
        }
    }
}
