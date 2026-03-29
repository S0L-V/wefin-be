package com.solv.wefin.web.market;

import com.solv.wefin.domain.market.service.MarketSnapshotService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class MarketAdminController {

    private final MarketSnapshotService marketSnapshotService;

    /**
     * 시장 지표를 수동으로 수집한다.
     */
    @PostMapping("/collect")
    public ApiResponse<String> collectNow() {
        marketSnapshotService.collectAndSave();
        return ApiResponse.success("시장 지표 수집 완료");
    }
}
