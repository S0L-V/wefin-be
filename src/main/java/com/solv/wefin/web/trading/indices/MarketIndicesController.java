package com.solv.wefin.web.trading.indices;

import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.SparklineInterval;
import com.solv.wefin.domain.trading.indices.service.MarketIndicesService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.indices.dto.MarketIndicesResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/market/indices")
public class MarketIndicesController {

    private final MarketIndicesService marketIndicesService;

    @GetMapping
    public ApiResponse<MarketIndicesResponse> getIndices(
        @RequestParam(defaultValue = "1m") String interval,
        @RequestParam(defaultValue = "30") @Min(1) @Max(80) int sparklinePoints
    ) {
        SparklineInterval parsedInterval = SparklineInterval.fromLabel(interval);
        List<IndexQuote> quotes = marketIndicesService.getAllIndices(parsedInterval, sparklinePoints);
        return ApiResponse.success(MarketIndicesResponse.from(quotes));
    }
}
