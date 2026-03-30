package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.dto.StockSearchResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class MarketController {

    private final MarketService marketService;
    private final StockService stockService;

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
}
