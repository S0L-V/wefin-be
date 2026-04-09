package com.solv.wefin.domain.news.summary.service;

/**
 * AI 요약 처리 중 클러스터 기사 집합이 변경되어 저장을 건너뛰어야 할 때 발생한다.
 * 요약 생성 실패(FAILED)가 아니라 재시도 대상이므로 markFailed를 호출하지 않는다
 */
public class StaleClusterException extends RuntimeException {

    public StaleClusterException(String message) {
        super(message);
    }
}
