package com.solv.wefin.web.news;

import com.solv.wefin.domain.news.tagging.service.RelevanceRejudgeService;
import com.solv.wefin.domain.news.tagging.service.RelevanceRejudgeService.RejudgeSummary;
import com.solv.wefin.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 금융 관련성 재판정 관리자 엔드포인트 (local/dev only).
 */
@Profile({"local", "dev", "desktop"})
@RestController
@RequestMapping("/api/admin/news/relevance")
@RequiredArgsConstructor
public class RelevanceAdminController {

    private static final int DEFAULT_PENDING_LIMIT = 100;

    private final RelevanceRejudgeService relevanceRejudgeService;

    /**
     * 지정된 기사 ID 목록을 재판정한다.
     */
    @PostMapping("/rejudge")
    public ApiResponse<RejudgeSummary> rejudgeByIds(@Valid @RequestBody RejudgeRequest request) {
        RejudgeSummary summary = relevanceRejudgeService.rejudgeByIds(request.articleIds());
        return ApiResponse.success(summary);
    }

    /**
     * PENDING 상태 기사를 배치로 재판정한다.
     */
    @PostMapping("/rejudge/pending")
    public ApiResponse<RejudgeSummary> rejudgePending(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_PENDING_LIMIT) int limit) {
        RejudgeSummary summary = relevanceRejudgeService.rejudgePending(limit);
        return ApiResponse.success(summary);
    }

    public record RejudgeRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> articleIds) {}
}
