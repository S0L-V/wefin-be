package com.solv.wefin.web.game.stock;

import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.service.StockSearchService;
import com.solv.wefin.global.common.ApiResponse;
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
}
