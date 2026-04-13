package com.solv.wefin.domain.news.cluster.dto;

/**
 * 피드 목록 카드용 출처 정보 (언론사명 + 원본 URL)
 *
 * 피드 카드에서는 title 없이 언론사만 노출하므로 상세용 ArticleSourceInfo와 분리되어 있다
 */
public record SourceInfo(String publisherName, String url) {
}
