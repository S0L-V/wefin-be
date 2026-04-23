package com.solv.wefin.web.market.trend;

import com.solv.wefin.domain.market.trend.scheduler.MarketTrendScheduler;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 금융 동향 수동 트리거 (로컬/개발 전용)
 */
@Profile({"local", "dev", "desktop"})
@RestController
@RequestMapping("/api/admin/market-trends")
@RequiredArgsConstructor
public class MarketTrendAdminController {

    private final MarketTrendScheduler marketTrendScheduler;

    @PostMapping("/trigger")
    public ApiResponse<String> trigger() {
        boolean executed = marketTrendScheduler.execute();
        if (!executed) {
            throw new BusinessException(ErrorCode.MARKET_TREND_ALREADY_RUNNING);
        }
        return ApiResponse.success("금융 동향 생성 배치 실행 완료");
    }
}
