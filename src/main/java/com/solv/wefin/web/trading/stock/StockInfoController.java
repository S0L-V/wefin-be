package com.solv.wefin.web.trading.stock;

import com.solv.wefin.domain.trading.stock.service.StockInfoService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.stock.dto.response.StockInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockInfoController {

    private final StockInfoService stockInfoService;

    @GetMapping("/{code}/info")
    public ApiResponse<StockInfoResponse> getStockInfo(@PathVariable String code) {
        return ApiResponse.success(stockInfoService.getStockInfo(code));
    }
}
