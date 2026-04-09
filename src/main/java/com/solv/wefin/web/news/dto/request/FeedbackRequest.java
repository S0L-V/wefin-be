package com.solv.wefin.web.news.dto.request;

import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback.FeedbackType;
import jakarta.validation.constraints.NotNull;

/**
 * 클러스터 피드백 요청 DTO
 *
 * @param type 피드백 유형 (HELPFUL 또는 NOT_HELPFUL). @Valid에 의한 Bean Validation으로 검증된다
 */
public record FeedbackRequest(
        @NotNull(message = "type은 필수입니다")
        FeedbackType type
) {
}
