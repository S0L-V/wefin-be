package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterDetailResult;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.news.dto.response.ClusterDetailResponse;
import com.solv.wefin.web.news.dto.response.ClusterFeedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 뉴스 클러스터 조회 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news/clusters")
public class NewsClusterController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TAG_CODES = 20;
    private static final Set<String> VALID_SORT_VALUES = Set.of("publishedAt", "updatedAt");

    private final NewsClusterQueryService newsClusterQueryService;

    /**
     * 뉴스 클러스터 피드 목록을 조회한다.
     *
     * 필터링 방법 (tab과 tagType/tagCodes는 상호 배타적):
     * - tab: 홈 카테고리 탭 (ALL/FINANCE/TECH/INDUSTRY/ENERGY/BIO/CRYPTO)
     * - tagType + tagCodes: 특정 태그들로 필터 (SECTOR/STOCK + 태그코드 목록)
     *
     * @param cursor "timestamp_id" 형식의 커서 (첫 페이지면 null)
     * @param pageSize 페이지 크기 (기본 10, 최대 50)
     * @param tab 카테고리 탭 (기본 ALL)
     * @param tagType 태그 유형 (SECTOR 또는 STOCK)
     * @param tagCodes 태그 코드 목록 (tagType과 함께 사용)
     * @param sort 정렬 기준 (publishedAt 또는 updatedAt, 기본 publishedAt)
     * @param userId 사용자 ID (비인증 시 null)
     */
    @GetMapping
    public ApiResponse<ClusterFeedResponse> getFeed(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(name = "tab", defaultValue = "ALL") String tab,
            @RequestParam(name = "tagType", required = false) String tagType,
            @RequestParam(name = "tagCodes", required = false) List<String> tagCodes,
            @RequestParam(name = "sort", defaultValue = "publishedAt") String sort,
            @AuthenticationPrincipal UUID userId
    ) {
        String normalizedSort = sort.trim();
        if (!VALID_SORT_VALUES.contains(normalizedSort)) {
            throw new BusinessException(ErrorCode.FEED_SORT_UNSUPPORTED);
        }

        String normalizedTagType = tagType == null ? null : tagType.trim();
        boolean hasTagType = normalizedTagType != null && !normalizedTagType.isEmpty();
        boolean hasTagCodes = tagCodes != null && !tagCodes.isEmpty();
        if (hasTagType != hasTagCodes) {
            throw new BusinessException(ErrorCode.FEED_TAG_PARAMS_INCOMPLETE);
        }

        TagType resolvedTagType = null;
        List<String> resolvedTagCodes = null;
        if (hasTagType) {
            resolvedTagType = TagType.fromStringOrNull(normalizedTagType);
            if (resolvedTagType == null || !resolvedTagType.isFilterable()) {
                throw new BusinessException(ErrorCode.FEED_TAG_TYPE_UNSUPPORTED);
            }
            if (!"ALL".equalsIgnoreCase(tab)) {
                throw new BusinessException(ErrorCode.FEED_TAG_AND_TAB_CONFLICT);
            }
            boolean shouldUpperCase = resolvedTagType == TagType.SECTOR;
            resolvedTagCodes = tagCodes.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> shouldUpperCase ? s.toUpperCase(java.util.Locale.ROOT) : s)
                    .distinct()
                    .limit(MAX_TAG_CODES)
                    .toList();
            if (resolvedTagCodes.isEmpty()) {
                throw new BusinessException(ErrorCode.FEED_TAG_CODES_EMPTY);
            }
        }

        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        OffsetDateTime cursorTime = null;
        Long cursorId = null;

        if (cursor != null) {
            try {
                String[] parts = cursor.split("_", 2);
                if (parts.length != 2) {
                    throw new NumberFormatException("underscore missing");
                }
                cursorTime = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC);
                cursorId = Long.parseLong(parts[1]);
            } catch (NumberFormatException | java.time.DateTimeException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 cursor 형식입니다: " + cursor);
            }
        }

        ClusterFeedResult result = newsClusterQueryService.getFeed(
                cursorTime, cursorId, pageSize, userId, tab, normalizedSort,
                resolvedTagType, resolvedTagCodes);

        return ApiResponse.success(ClusterFeedResponse.from(result));
    }

    /**
     * 뉴스 클러스터 상세 정보를 조회한다.
     * 섹션(소제목 + 단락)과 섹션별 근거 기사 출처를 포함한다
     *
     * @param clusterId 클러스터 ID
     * @param userId 사용자 ID (비인증 시 null)
     */
    @GetMapping("/{clusterId}")
    public ApiResponse<ClusterDetailResponse> getDetail(
            @PathVariable Long clusterId,
            @AuthenticationPrincipal UUID userId
    ) {
        ClusterDetailResult result = newsClusterQueryService.getDetail(clusterId, userId);
        return ApiResponse.success(ClusterDetailResponse.from(result));
    }
}
