package com.solv.wefin.web.market.trend;

import com.solv.wefin.domain.market.trend.dto.MarketTrendOverview;
import com.solv.wefin.domain.market.trend.service.MarketTrendQueryService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.market.trend.dto.MarketTrendOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘의 금융 동향 API
 */
@RestController
@RequestMapping("/api/market-trends")
@RequiredArgsConstructor
public class MarketTrendController {

    private final MarketTrendQueryService queryService;

    /**
     * 오늘 동향 조회 (비회원 가능)
     */
    @GetMapping("/overview")
    public ApiResponse<MarketTrendOverviewResponse> getOverview() {
        MarketTrendOverview overview = queryService.getOverview();
        return ApiResponse.success(MarketTrendOverviewResponse.from(overview));
    }
}
