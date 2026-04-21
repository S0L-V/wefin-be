package com.solv.wefin.domain.news.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 뉴스 클러스터 조회수 랭킹({@code sort=view}) 운영 설정
 *
 * 윈도우 길이, 배치 주기, 페이지 상한, markRead throttle 관리
 * 운영 중 트래픽과 데이터 분포에 따라 환경변수로 튜닝
 *
 * @param windowHours               recent_view_count 집계 윈도우 (시간). 3h는 휘발성↔관성 균형의 가설값
 * @param aggregationIntervalSeconds HotClusterAggregationScheduler 실행 주기 (초)
 * @param initialDelaySeconds       앱 기동 후 첫 집계 실행까지 대기 시간 (초). warmup/rolling 배포 시 부하 분산용
 * @param maxSize                   sort=view 요청의 응답 항목 상한. hot feed UX 특성상 Top N 고정
 * @param markReadThrottleSeconds   touchReadAtIfStale 의 throttle 기준. 이 시간 내 재호출은 write skip
 */
@Validated
@ConfigurationProperties(prefix = "news.hot")
public record NewsHotProperties(
        @Min(1) @Max(24) int windowHours,
        @Min(60) @Max(3600) int aggregationIntervalSeconds,
        @Min(0) @Max(600) int initialDelaySeconds,
        @Min(1) @Max(50) int maxSize,
        @Min(1) @Max(3600) int markReadThrottleSeconds
) {
}
