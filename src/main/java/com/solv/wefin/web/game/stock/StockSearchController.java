package com.solv.wefin.web.game.stock;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository.SectorKeywordCount;
import com.solv.wefin.domain.game.stock.service.StockSearchService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.stock.dto.response.SectorResponse;
import com.solv.wefin.web.game.stock.dto.response.StockSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms/{roomId}/stocks")
public class StockSearchController {

    private final StockSearchService stockSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StockSearchResponse>>> searchStocks(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID roomId,
            @RequestParam String keyword) {

        List<StockDaily> stocks = stockSearchService.searchStocks(roomId, userId, keyword);

        List<StockSearchResponse> response = stocks.stream()
                .map(StockSearchResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 섹터 목록 조회 (키워드 개수 포함) */
    @GetMapping("/sectors")
    public ResponseEntity<ApiResponse<List<SectorResponse>>> getSectors(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID roomId) {

        List<SectorKeywordCount> sectors = stockSearchService.getSectors(roomId, userId);

        List<SectorResponse> response = sectors.stream()
                .map(SectorResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 특정 섹터의 키워드 목록 조회 */
    @GetMapping("/sectors/{sector}/keywords")
    public ResponseEntity<ApiResponse<List<String>>> getKeywords(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID roomId,
            @PathVariable String sector) {

        List<String> keywords = stockSearchService.getKeywords(roomId, userId, sector);
        return ResponseEntity.ok(ApiResponse.success(keywords));
    }

    /** 특정 섹터+키워드의 종목 목록 조회 (현재 턴 종가 포함) */
    @GetMapping("/sectors/{sector}/stocks")
    public ResponseEntity<ApiResponse<List<StockSearchResponse>>> getStocksByKeyword(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID roomId,
            @PathVariable String sector,
            @RequestParam String keyword) {

        List<StockDaily> stocks = stockSearchService.getStocksByKeyword(roomId, userId, sector, keyword);

        List<StockSearchResponse> response = stocks.stream()
                .map(StockSearchResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
