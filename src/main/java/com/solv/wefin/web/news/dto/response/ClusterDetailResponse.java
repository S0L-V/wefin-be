package com.solv.wefin.web.news.dto.response;

import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ArticleSourceInfo;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterDetailResult;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.SectionDetail;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.StockInfo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 클러스터 상세 조회 API 응답 DTO
 *
 * 피드 목록 정보에 더해 섹션(소제목 + 단락 + 근거 기사)을 포함한다
 */
public record ClusterDetailResponse(
        Long clusterId,
        String title,
        String summary,
        String thumbnailUrl,
        OffsetDateTime publishedAt,
        int sourceCount,
        List<ArticleSourceResponse> sources,
        List<StockResponse> relatedStocks,
        List<String> marketTags,
        boolean isRead,
        List<SectionResponse> sections
) {

    /**
     * Service 결과 → API 응답 DTO 변환
     */
    public static ClusterDetailResponse from(ClusterDetailResult result) {
        List<ArticleSourceResponse> sources = result.sources().stream()
                .map(ArticleSourceResponse::from)
                .toList();

        List<StockResponse> stocks = result.relatedStocks().stream()
                .map(StockResponse::from)
                .toList();

        List<SectionResponse> sections = result.sections().stream()
                .map(SectionResponse::from)
                .toList();

        return new ClusterDetailResponse(
                result.clusterId(), result.title(), result.summary(),
                result.thumbnailUrl(), result.publishedAt(),
                result.sourceCount(), sources, stocks, result.marketTags(),
                result.isRead(), sections
        );
    }

    public record StockResponse(String code, String name) {
        public static StockResponse from(StockInfo stock) {
            return new StockResponse(stock.code(), stock.name());
        }
    }

    /**
     * 요약 섹션 응답 DTO (소제목 + 단락 + 근거 기사 출처)
     */
    public record SectionResponse(
            int sectionOrder,
            String heading,
            String body,
            int sourceCount,
            List<ArticleSourceResponse> sources
    ) {
        public static SectionResponse from(SectionDetail detail) {
            List<ArticleSourceResponse> sources = detail.sources().stream()
                    .map(ArticleSourceResponse::from)
                    .toList();
            return new SectionResponse(
                    detail.sectionOrder(), detail.heading(), detail.body(),
                    detail.sourceCount(), sources
            );
        }
    }

    /**
     * 출처 카드용 기사 정보
     */
    public record ArticleSourceResponse(
            Long articleId,
            String title,
            String publisherName,
            String url
    ) {
        public static ArticleSourceResponse from(ArticleSourceInfo info) {
            return new ArticleSourceResponse(
                    info.articleId(), info.title(), info.publisherName(), info.url()
            );
        }
    }
}
