package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.service.NewsTagQueryService;
import com.solv.wefin.domain.news.article.service.NewsTagQueryService.PopularTag;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 뉴스 태그 조회 API
 *
 * ACTIVE 클러스터에 속한 기사들의 인기 태그를 제공한다
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news/tags")
public class NewsTagController {

    private final NewsTagQueryService newsTagQueryService;

    /**
     * 태그 목록을 조회한다
     *
     * @param type 태그 유형 (SECTOR 또는 STOCK)
     * @param limit 최대 조회 수 (기본 20, 0이면 전체, 음수 불가)
     */
    @GetMapping("/popular")
    public ApiResponse<List<PopularTagResponse>> getPopularTags(
            @RequestParam(name = "type") String type,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        TagType tagType = TagType.fromStringOrNull(type.trim());
        if (tagType == null || !tagType.isFilterable()) {
            throw new BusinessException(ErrorCode.TAG_TYPE_UNSUPPORTED);
        }
        if (limit < 0) {
            throw new BusinessException(ErrorCode.TAG_LIMIT_INVALID);
        }

        List<PopularTag> tags = newsTagQueryService.getPopularTags(tagType, limit);

        List<PopularTagResponse> response = tags.stream()
                .map(t -> new PopularTagResponse(t.code(), t.name(), t.clusterCount()))
                .toList();

        return ApiResponse.success(response);
    }

    public record PopularTagResponse(String code, String name, long clusterCount) {
    }
}
