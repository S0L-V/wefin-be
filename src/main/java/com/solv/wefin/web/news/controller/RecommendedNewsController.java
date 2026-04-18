package com.solv.wefin.web.news.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.recommendation.service.NewsRecommendationService;
import com.solv.wefin.domain.news.recommendation.service.NewsRecommendationService.RecommendationResult;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.news.dto.response.RecommendedNewsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 추천 뉴스(나를 위한 뉴스) API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news/recommended")
public class RecommendedNewsController {

    private final NewsRecommendationService newsRecommendationService;
    private final ObjectMapper objectMapper;

    /**
     * 추천 뉴스 카드를 조회한다
     *
     * 캐시가 유효하면 반환하고, 없거나 stale이면 AI로 생성한다.
     * 관심사가 없으면 빈 카드 목록을 반환한다
     */
    @GetMapping
    public ApiResponse<RecommendedNewsResponse> getRecommendedNews(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        RecommendationResult result = newsRecommendationService.getRecommendedCards(userId);
        return ApiResponse.success(RecommendedNewsResponse.from(result, objectMapper));
    }

    /**
     * 추천 뉴스 카드를 교체한다 (다른 뉴스 불러오기)
     *
     * 이전에 사용된 관심사를 제외하고 새 카드를 생성한다.
     * 모든 관심사가 소진되면 hasMore=false를 반환한다
     */
    @PostMapping("/refresh")
    public ApiResponse<RecommendedNewsResponse> refreshRecommendedNews(
            @AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        RecommendationResult result = newsRecommendationService.refreshCards(userId);
        return ApiResponse.success(RecommendedNewsResponse.from(result, objectMapper));
    }
}
