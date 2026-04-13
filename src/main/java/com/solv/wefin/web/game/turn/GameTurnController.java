package com.solv.wefin.web.game.turn;

import com.solv.wefin.domain.game.turn.entity.GameTurn;
import com.solv.wefin.domain.game.turn.service.GameTurnService;
import com.solv.wefin.domain.game.turn.service.TurnAdvanceService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.turn.dto.response.CurrentTurnResponse;
import com.solv.wefin.web.game.turn.dto.response.TurnAdvanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/turns")
@RequiredArgsConstructor
public class GameTurnController {

    private final GameTurnService gameTurnService;
    private final TurnAdvanceService turnAdvanceService;

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CurrentTurnResponse>> getCurrentTurn(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameTurn turn = gameTurnService.getCurrentTurn(roomId, userId);
        CurrentTurnResponse response = CurrentTurnResponse.from(turn);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/next")
    public ResponseEntity<ApiResponse<TurnAdvanceResponse>> advanceTurn(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId) {

        GameTurn nextTurn = turnAdvanceService.advanceTurn(roomId, userId);

        TurnAdvanceResponse response = (nextTurn != null)
                ? TurnAdvanceResponse.from(nextTurn)
                : TurnAdvanceResponse.finished();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
