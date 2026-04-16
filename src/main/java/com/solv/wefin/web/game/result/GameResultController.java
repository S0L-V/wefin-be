package com.solv.wefin.web.game.result;

import com.solv.wefin.domain.game.result.dto.GameEndInfo;
import com.solv.wefin.domain.game.result.service.GameEndService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.result.dto.response.GameEndResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<GameEndResponse>> endGame(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameEndInfo info = gameEndService.endGame(roomId, userId);
        GameEndResponse response = GameEndResponse.from(info);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
