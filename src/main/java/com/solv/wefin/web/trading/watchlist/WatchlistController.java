package com.solv.wefin.web.trading.watchlist;

import com.solv.wefin.domain.trading.watchlist.dto.WatchlistInfo;
import com.solv.wefin.domain.trading.watchlist.service.WatchlistService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.trading.watchlist.dto.WatchlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ApiResponse<List<WatchlistResponse>> getWatchlist(
            @AuthenticationPrincipal UUID userId) {
        List<WatchlistInfo> items = watchlistService.getStockList(userId);
        List<WatchlistResponse> response = items.stream()
                .map(item -> WatchlistResponse.from(item.stock(), item.price()))
                .toList();
        return ApiResponse.success(response);
    }

    @PostMapping("/{code}")
    public ApiResponse<Void> addWatchlist(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String code) {
        watchlistService.addUserInterest(userId, code);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> deleteWatchlist(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String code) {
        watchlistService.deleteUserInterest(userId, code);
        return ApiResponse.success(null);
    }

}
