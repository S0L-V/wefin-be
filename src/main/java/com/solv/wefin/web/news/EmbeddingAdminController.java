package com.solv.wefin.web.news;

import com.solv.wefin.domain.news.embedding.batch.EmbeddingScheduler;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev", "desktop"})
@RestController
@RequestMapping("/api/admin/news/embeddings")
@RequiredArgsConstructor
public class EmbeddingAdminController {

    private final EmbeddingScheduler embeddingScheduler;

    /**
     * 임베딩 생성을 수동으로 트리거한다.
     */
    @PostMapping("/generate")
    public ApiResponse<String> generateNow() {
        boolean executed = embeddingScheduler.execute();
        if (executed) {
            return ApiResponse.success("임베딩 생성 완료");
        }
        return ApiResponse.success("임베딩 생성을 건너뜁니다 (이미 실행 중이거나 오류 발생)");
    }
}
