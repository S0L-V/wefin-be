package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.news.dto.response.ClusterFeedResponse;
import lombok.RequiredArgsConstructor;
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
     * @param userId 사용자 ID (인증 시 헤더에서 주입, 없으면 null)
     */
    @GetMapping
    public ApiResponse<ClusterFeedResponse> getFeed(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "pageSize", defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(name = "tab", defaultValue = "ALL") String tab,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId
    ) {
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        OffsetDateTime cursorPublishedAt = null;
        Long cursorId = null;

        if (cursor != null && cursor.contains("_")) {
            try {
                String[] parts = cursor.split("_", 2);
                cursorPublishedAt = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC);
                cursorId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 cursor 형식입니다: " + cursor);
            }
        }

        ClusterFeedResult result = newsClusterQueryService.getFeed(
                cursorPublishedAt, cursorId, pageSize, userId, tab);

        return ApiResponse.success(ClusterFeedResponse.from(result));
    }
}
