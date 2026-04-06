package com.solv.wefin.web.news;

import com.solv.wefin.domain.news.summary.batch.SummaryScheduler;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/news/summary")
@RequiredArgsConstructor
public class SummaryAdminController {

    private final SummaryScheduler summaryScheduler;

    /**
     * AI 요약 생성을 수동으로 트리거한다.
     */
    @PostMapping("/trigger")
    public ApiResponse<String> triggerSummary() {
        boolean executed = summaryScheduler.execute();
        if (!executed) {
            throw new BusinessException(ErrorCode.SUMMARY_ALREADY_RUNNING);
        }
        return ApiResponse.success("요약 생성 배치 실행 완료");
    }
}
