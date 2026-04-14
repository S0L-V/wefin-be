package com.solv.wefin.web.game.vote;

import com.solv.wefin.domain.game.vote.VoteSession;
import com.solv.wefin.domain.game.vote.service.GameVoteService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.game.vote.dto.request.VoteRequest;
import com.solv.wefin.web.game.vote.dto.response.VoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/votes")
@RequiredArgsConstructor
public class GameVoteController {

    private final GameVoteService gameVoteService;

    @PostMapping
    public ResponseEntity<ApiResponse<VoteResponse>> vote(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody VoteRequest request) {

        VoteSession session = gameVoteService.vote(roomId, userId, request.getAgree());
        VoteResponse response = VoteResponse.from(session);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
