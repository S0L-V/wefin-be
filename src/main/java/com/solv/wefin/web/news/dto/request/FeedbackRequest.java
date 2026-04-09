package com.solv.wefin.web.news.dto.request;

import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import jakarta.validation.constraints.NotNull;

/**
 * 클러스터 피드백 요청 DTO
 *
 * @param type 피드백 유형 (HELPFUL 또는 NOT_HELPFUL). Jackson이 역직렬화 시 검증한다
 */
public record FeedbackRequest(
        @NotNull(message = "type은 필수입니다")
        FeedbackType type
) {
}
