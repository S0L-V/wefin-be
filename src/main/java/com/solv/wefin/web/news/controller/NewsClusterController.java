package com.solv.wefin.web.news.controller;

import com.solv.wefin.domain.news.cluster.service.ClusterInteractionService;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterDetailResult;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.domain.news.config.NewsHotProperties;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.news.dto.request.FeedbackRequest;
import com.solv.wefin.web.news.dto.response.ClusterDetailResponse;
import com.solv.wefin.web.news.dto.response.ClusterFeedResponse;
import jakarta.validation.Valid;
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
    private static final String SORT_VIEW = "view";
    private static final Set<String> VALID_SORT_VALUES = Set.of("publishedAt", "updatedAt", SORT_VIEW);

    private final NewsClusterQueryService newsClusterQueryService;
    private final ClusterInteractionService clusterInteractionService;
    private final NewsHotProperties newsHotProperties;

    /**
     * 뉴스 클러스터 피드 목록을 조회한다.
     *
     * 필터링 방법 (tab과 tagType/tagCodes는 상호 배타적):
     * - tab: 홈 카테고리 탭 (ALL/FINANCE/TECH/INDUSTRY/ENERGY/BIO/CRYPTO)
     * - tagType + tagCodes: 특정 태그들로 필터 (SECTOR/STOCK + 태그코드 목록)
     *
     * 정렬 옵션:
     * - publishedAt / updatedAt: 시간 기준 커서 페이지네이션 지원 (size 최대 50)
     * - view: 최근 N시간(기본 3h) 윈도우 내 고유 뷰어 수 기준 Top N. 페이지네이션 미지원,
     *   size 는 {@code news.hot.max-size} 상한 (초과 시 400 INVALID_INPUT).
     *   cursor 를 보내도 무시되며 {@code hasNext=false}, {@code nextCursor=null}.
     *   응답의 {@code lastAggregatedAt} 은 배치 마지막 성공 시각이며, 첫 배치 전이면 null.
     *   FE 는 임계(예: 15분) 초과 시 {@code sort=publishedAt} 으로 자동 폴백 권장.
     *
     * @param cursor "timestamp_id" 형식의 커서 (첫 페이지면 null, sort=view 면 무시)
     * @param pageSize 페이지 크기 (기본 10, 최대 50 / sort=view 는 news.hot.max-size)
     * @param tab 카테고리 탭 (기본 ALL)
     * @param tagType 태그 유형 (SECTOR 또는 STOCK)
     * @param tagCodes 태그 코드 목록 (tagType과 함께 사용)
     * @param sort 정렬 기준 (publishedAt / updatedAt / view, 기본 publishedAt)
     * @param userId 사용자 ID (비인증 시 null)
     */
    @GetMapping
    public ApiResponse<ClusterFeedResponse> getFeed(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", required = false) Integer pageSizeParam,
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
        boolean isHotSort = SORT_VIEW.equals(normalizedSort);

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

        int pageSize;
        if (isHotSort) {
            int hotMaxSize = newsHotProperties.maxSize();
            if (pageSizeParam == null) {
                // size 미지정 — DEFAULT_PAGE_SIZE 와 hotMaxSize 중 작은 쪽 사용.
                // 운영자가 max-size 를 작게 튜닝했을 때 "size 안 보냈는데 거절당함" UX 방지.
                pageSize = Math.min(DEFAULT_PAGE_SIZE, hotMaxSize);
            } else if (pageSizeParam < 1 || pageSizeParam > hotMaxSize) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "sort=view 의 size 는 1 ~ " + hotMaxSize + " 사이여야 합니다: " + pageSizeParam);
            } else {
                pageSize = pageSizeParam;
            }
        } else {
            int raw = pageSizeParam == null ? DEFAULT_PAGE_SIZE : pageSizeParam;
            pageSize = Math.min(Math.max(raw, 1), MAX_PAGE_SIZE);
        }

        OffsetDateTime cursorTime = null;
        Long cursorId = null;

        // sort=view 는 cursor 파라미터를 무시한다 (forward compat: 구 클라이언트가 붙여 보내도 에러 없이 Top N 반환)
        if (!isHotSort && cursor != null) {
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

    /**
     * 클러스터 읽음을 기록한다.
     *
     * 비회원(userId=null)은 무시한다.
     *
     * 호출 계약(FE 권장):
     * - 상세 진입 세션 당 1회
     * - 같은 세션 내 재진입은 FE 에서 30초 쿨다운 debounce
     * 서버는 {@code news.hot.mark-read-throttle-seconds} 내 반복 호출에 대해
     * read_at UPDATE 를 skip 하여 방어 계층을 이중으로 둔다.
     *
     * @param clusterId 클러스터 ID
     * @param userId 사용자 ID (비인증 시 null)
     */
    @PostMapping("/{clusterId}/read")
    public ApiResponse<Void> markRead(
            @PathVariable Long clusterId,
            @AuthenticationPrincipal UUID userId
    ) {
        if (userId != null) {
            clusterInteractionService.markRead(userId, clusterId);
        }
        return ApiResponse.success(null);
    }

    /**
     * 클러스터에 피드백을 남긴다.
     * 1회만 가능하며, 이미 피드백한 경우 409를 반환한다.
     * 인증 필수
     *
     * @param clusterId 클러스터 ID
     * @param request 피드백 유형 (HELPFUL / NOT_HELPFUL)
     * @param userId 사용자 ID
     */
    @PostMapping("/{clusterId}/feedback")
    public ApiResponse<Void> submitFeedback(
            @PathVariable Long clusterId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        clusterInteractionService.submitFeedback(userId, clusterId, request.type());
        return ApiResponse.success(null);
    }
}
