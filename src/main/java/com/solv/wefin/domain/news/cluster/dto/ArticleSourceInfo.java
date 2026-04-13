package com.solv.wefin.domain.news.cluster.dto;

/**
 * 상세 화면 출처 카드용 기사 정보 (id + title + 언론사 + 원본 URL)
 *
 * 상세/섹션 출처 양쪽에서 공용으로 사용된다
 */
public record ArticleSourceInfo(
        Long articleId,
        String title,
        String publisherName,
        String url
) {
}
