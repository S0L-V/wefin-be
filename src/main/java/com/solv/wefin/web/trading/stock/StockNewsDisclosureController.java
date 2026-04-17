package com.solv.wefin.web.trading.stock;

import com.solv.wefin.domain.trading.dart.dto.DartDisclosureInfo;
import com.solv.wefin.domain.trading.dart.service.DartDisclosureService;
import com.solv.wefin.domain.trading.stock.news.dto.StockNewsInfo;
import com.solv.wefin.domain.trading.stock.news.service.StockNewsService;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockNewsDisclosureController {

    private final StockNewsService stockNewsService;
    private final DartDisclosureService dartDisclosureService;

    @GetMapping("/{code}/news")
    public ApiResponse<StockNewsInfo> getNews(@PathVariable String code) {
        return ApiResponse.success(stockNewsService.getNews(code));
    }

    @GetMapping("/{code}/disclosures")
    public ApiResponse<DartDisclosureInfo> getDisclosures(@PathVariable String code) {
        return ApiResponse.success(dartDisclosureService.getDisclosures(code));
    }
}
