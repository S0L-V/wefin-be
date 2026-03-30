package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.dto.StockSearchResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
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
    public PriceResponse getPrice(@PathVariable String code) {
        return marketService.getPrice(code);
    }

    @GetMapping("/{code}/orderbook")
    public OrderbookResponse getOrderbook(@PathVariable String code) {
        return marketService.getOrderbook(code);
    }

    @GetMapping("/search")
    public List<StockSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String market) {
        return stockService.search(keyword, market);
    }
}
