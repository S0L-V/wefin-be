package com.solv.wefin.domain.news.cluster.dto;

/**
 * 클러스터 관련 종목 정보 (code + canonical name)
 *
 * 피드 아이템과 상세 응답 양쪽에서 공용으로 사용된다
 */
public record StockInfo(String code, String name) {
}
