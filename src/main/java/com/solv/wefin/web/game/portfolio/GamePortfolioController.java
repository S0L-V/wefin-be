package com.solv.wefin.web.game.portfolio;

import com.solv.wefin.domain.game.participant.dto.HoldingInfo;
import com.solv.wefin.domain.game.participant.dto.PortfolioInfo;
import com.solv.wefin.domain.game.participant.service.GamePortfolioService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.portfolio.dto.response.HoldingResponse;
import com.solv.wefin.web.game.portfolio.dto.response.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class GamePortfolioController {

    private final GamePortfolioService portfolioService;

    @GetMapping("/portfolio")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getPortfolio(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        PortfolioInfo info = portfolioService.getPortfolio(roomId, userId);
        PortfolioResponse response = PortfolioResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/holdings")
    public ResponseEntity<ApiResponse<List<HoldingResponse>>> getHoldings(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        List<HoldingResponse> response = portfolioService.getHoldings(roomId, userId).stream()
                .map(HoldingResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
