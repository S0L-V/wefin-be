package com.solv.wefin.web.market;

import com.solv.wefin.domain.market.service.MarketSnapshotService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.market.dto.response.MarketSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class MarketAdminController {

    private final MarketSnapshotService marketSnapshotService;

    /**
     * 저장된 시장 지표 스냅샷 전체를 조회한다.
     */
    @GetMapping("/snapshots")
    public ApiResponse<List<MarketSnapshotResponse>> getSnapshots() {
        List<MarketSnapshotResponse> snapshots = marketSnapshotService.getAllSnapshots()
                .stream()
                .map(MarketSnapshotResponse::from)
                .toList();
        return ApiResponse.success(snapshots);
    }

    /**
     * 시장 지표를 수동으로 수집한다.
     */
    @PostMapping("/collect")
    public ApiResponse<String> collectNow() {
        marketSnapshotService.collectAndSave();
        return ApiResponse.success("시장 지표 수집 완료");
    }
}
