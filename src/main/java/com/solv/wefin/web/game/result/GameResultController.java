package com.solv.wefin.web.game.result;

import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import com.solv.wefin.domain.game.result.service.GameEndService;
import com.solv.wefin.domain.game.result.service.GameResultService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.result.dto.response.GameEndResponse;
import com.solv.wefin.web.game.result.dto.response.GameResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class GameResultController {

    private final GameEndService gameEndService;
    private final GameResultService gameResultService;

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<GameEndResponse>> endGame(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameEndInfo info = gameEndService.endGame(roomId, userId);
        GameEndResponse response = GameEndResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/result")
    public ResponseEntity<ApiResponse<GameResultResponse>> getGameResult(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameResultInfo info = gameResultService.getGameResult(roomId, userId);
        GameResultResponse response = GameResultResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
