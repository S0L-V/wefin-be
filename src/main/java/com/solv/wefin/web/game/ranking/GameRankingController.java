package com.solv.wefin.web.game.ranking;

import com.solv.wefin.domain.game.snapshot.service.GameRankingService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.ranking.dto.response.RankingResponse;
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
public class GameRankingController {

    private final GameRankingService rankingService;

    @GetMapping("/rankings")
    public ResponseEntity<ApiResponse<List<RankingResponse>>> getRankings(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        List<RankingResponse> response = rankingService.getRankings(roomId, userId).stream()
                .map(RankingResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
