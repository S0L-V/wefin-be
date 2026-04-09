package com.solv.wefin.web.game.stock;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.service.StockChartService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.stock.dto.response.ChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{symbol}")
public class StockChartController {

    private final StockChartService stockChartService;

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<List<ChartResponse>>> getChart(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String symbol,
            @RequestParam UUID roomId) {

        List<StockDaily> dailyData = stockChartService.getChart(symbol, roomId);

        List<ChartResponse> response = dailyData.stream()
                .map(ChartResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
