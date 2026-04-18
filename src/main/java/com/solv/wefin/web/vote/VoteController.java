package com.solv.wefin.web.vote;

import com.solv.wefin.domain.vote.dto.info.VoteDetailInfo;
import com.solv.wefin.domain.vote.dto.info.VoteInfo;
import com.solv.wefin.domain.vote.dto.info.VoteResultInfo;
import com.solv.wefin.domain.vote.service.VoteService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.vote.dto.request.CreateVoteRequest;
import com.solv.wefin.web.vote.dto.request.SubmitVoteRequest;
import com.solv.wefin.web.vote.dto.response.VoteDetailResponse;
import com.solv.wefin.web.vote.dto.response.VoteResponse;
import com.solv.wefin.web.vote.dto.response.VoteResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping
    public ApiResponse<VoteResponse> createVote(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateVoteRequest request
    ) {
        VoteInfo info = voteService.createVote(userId, request.toCommand());
        return ApiResponse.success(VoteResponse.from(info));
    }

    @GetMapping("/{voteId}")
    public ApiResponse<VoteDetailResponse> getVoteDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long voteId
    ) {
        VoteDetailInfo info =  voteService.getVoteDetail(userId, voteId);
        return ApiResponse.success(VoteDetailResponse.from(info));
    }

    @PostMapping("/{voteId}/answers")
    public ApiResponse<VoteResultResponse> submitVote(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long voteId,
            @Valid @RequestBody SubmitVoteRequest request
    ) {
        VoteResultInfo info = voteService.submitVote(userId, voteId, request.toCommand());
        return ApiResponse.success(VoteResultResponse.from(info));
    }

    @GetMapping("/{voteId}/result")
    public ApiResponse<VoteResultResponse> getVoteResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long voteId
    ) {
        VoteResultInfo info = voteService.getVoteResult(userId, voteId);
        return ApiResponse.success(VoteResultResponse.from(info));
    }
}
