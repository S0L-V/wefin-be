package com.solv.wefin.web.news;

import com.solv.wefin.domain.news.tagging.batch.TaggingScheduler;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/news/tagging")
@RequiredArgsConstructor
public class TaggingAdminController {

    private final TaggingScheduler taggingScheduler;

    /**
     * 태깅 생성을 수동으로 트리거한다.
     */
    @PostMapping("/generate")
    public ApiResponse<String> generateNow() {
        boolean executed = taggingScheduler.execute();
        if (executed) {
            return ApiResponse.success("태깅 생성 완료");
        }
        return ApiResponse.success("태깅 생성이 이미 실행 중입니다. 스킵합니다.");
    }
}
