package com.solv.wefin.web.news.controller;

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

    private final NewsClusterQueryService newsClusterQueryService;

    /**
     * 뉴스 클러스터 피드 목록을 조회한다.
     *
     * @param cursor "timestamp_id" 형식의 커서 (첫 페이지면 null)
     * @param pageSize 페이지 크기 (기본 10, 최대 50)
     * @param tab 카테고리 탭 (ALL/FINANCE/TECH/INDUSTRY/ENERGY/BIO/CRYPTO)
     * @param sort 정렬 기준 (publishedAt 또는 updatedAt, 기본 publishedAt)
     * @param userId 사용자 ID (비인증 시 null)
     */
    @GetMapping
    public ApiResponse<ClusterFeedResponse> getFeed(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(name = "tab", defaultValue = "ALL") String tab,
            @RequestParam(name = "sort", defaultValue = "publishedAt") String sort,
            @AuthenticationPrincipal UUID userId
    ) {
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
                cursorTime, cursorId, pageSize, userId, tab, sort);

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
