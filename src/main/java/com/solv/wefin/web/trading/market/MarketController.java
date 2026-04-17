package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.client.dto.RankingType;
import com.solv.wefin.domain.trading.market.client.dto.StockRankingItem;
import com.solv.wefin.domain.trading.market.client.dto.StockRankingResponse;
import com.solv.wefin.domain.trading.market.dto.CandleResponse;
import com.solv.wefin.domain.trading.market.dto.InvestorTrendResponse;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.dto.RecentTradeResponse;
import com.solv.wefin.domain.trading.market.service.InvestorTrendService;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.dto.StockSearchResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.common.ApiResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class MarketController {

    private final MarketService marketService;
    private final StockService stockService;
    private final InvestorTrendService investorTrendService;

    @GetMapping("/{code}/price")
    public ApiResponse<PriceResponse> getPrice(@PathVariable String code) {
        return ApiResponse.success(marketService.getPrice(code));
    }

    @GetMapping("/{code}/orderbook")
    public ApiResponse<OrderbookResponse> getOrderbook(@PathVariable String code) {
        return ApiResponse.success(marketService.getOrderbook(code));
    }

    @GetMapping("/search")
    public ApiResponse<List<StockSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String market) {
        return ApiResponse.success(stockService.search(keyword, market));
    }

    @GetMapping("/{code}/candles")
    public ApiResponse<List<CandleResponse>> getCandle(
            @PathVariable String code,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            @RequestParam String periodCode) {
        return ApiResponse.success(marketService.getCandles(code, start, end, periodCode));
    }

    @GetMapping("/{code}/trades/recent")
    public ApiResponse<List<RecentTradeResponse>> getRecentTrades(@PathVariable String code) {
        return ApiResponse.success(marketService.getRecentTrades(code));
    }

    @GetMapping("/{code}/investor-trend")
    public ApiResponse<InvestorTrendResponse> getInvestorTrend(@PathVariable String code) {
        return ApiResponse.success(investorTrendService.getInvestorTrend(code));
    }

    @GetMapping("/ranking")
    public ApiResponse<StockRankingResponse> getStockRanking(@RequestParam RankingType type,
                                                             @RequestParam(defaultValue = "30") @Min(1) @Max(50) int limit) {
        List<StockRankingItem> items = marketService.getStockRanking(type);
        List<StockRankingItem> limited = items.size() > limit
            ? items.subList(0, limit) : items;
        return ApiResponse.success(StockRankingResponse.from(limited));
    }

}