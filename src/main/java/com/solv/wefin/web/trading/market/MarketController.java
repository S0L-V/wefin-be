package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/{code}/price")
    public PriceResponse getPrice(@PathVariable String code) {
        return marketService.getPrice(code);
    }

    @GetMapping("/{code}/orderbook")
    public OrderbookResponse getOrderbook(@PathVariable String code) {
        return marketService.getOrderbook(code);
    }
}
