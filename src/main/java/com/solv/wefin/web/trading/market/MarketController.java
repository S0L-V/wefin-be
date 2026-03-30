package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/{code}/price")
    public ApiResponse<PriceResponse> getPrice(@PathVariable String code) {
        return ApiResponse.success(marketService.getPrice(code));
    }

    @GetMapping("/{code}/orderbook")
    public ApiResponse<OrderbookResponse> getOrderbook(@PathVariable String code) {
        return ApiResponse.success(marketService.getOrderbook(code));
    }
}
