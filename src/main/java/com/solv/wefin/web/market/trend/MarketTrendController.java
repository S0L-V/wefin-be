package com.solv.wefin.web.market.trend;

import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.service.MarketTrendQueryService;
import com.solv.wefin.domain.market.trend.service.PersonalizedMarketTrendService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.market.trend.dto.MarketTrendOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 오늘의 금융 동향 API
 */
@RestController
@RequestMapping("/api/market-trends")
@RequiredArgsConstructor
public class MarketTrendController {

    private final MarketTrendQueryService queryService;
    private final PersonalizedMarketTrendService personalizedService;

    /**
     * 오늘 동향 조회 (비회원 가능)
     */
    @GetMapping("/overview")
    public ApiResponse<MarketTrendOverviewResponse> getOverview() {
        MarketTrendOverview overview = queryService.getOverview();
        return ApiResponse.success(MarketTrendOverviewResponse.from(overview));
    }

    /**
     * 사용자 관심사 기반 맞춤 동향 조회
     *
     * 관심사 0개거나 매칭 클러스터가 없으면 overview와 동일한 페이로드를 {@code personalized=false}로 반환한다.
     * {@code cacheOnly=true}면 TTL(30분) 내 캐시만 반환하고 AI 호출은 수행하지 않는다.
     */
    @GetMapping("/personalized")
    public ApiResponse<MarketTrendOverviewResponse> getPersonalized(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(name = "cacheOnly", defaultValue = "false") boolean cacheOnly) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (cacheOnly) {
            return personalizedService.getCachedForUser(userId)
                    .map(MarketTrendOverviewResponse::from)
                    .map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.success(null));
        }
        MarketTrendOverview overview = personalizedService.getForUser(userId);
        return ApiResponse.success(MarketTrendOverviewResponse.from(overview));
    }
}
