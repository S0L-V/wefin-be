package com.solv.wefin.domain.news.summary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OpenAI 요약 생성 응답 DTO
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryResult {

    private String title;   // 클러스터 대표 제목 (50자 이내)
    private String summary; // 종합 요약 (팩트/분석/전망/영향, 200~400자)

    public boolean isEmpty() {
        return (title == null || title.isBlank()) || (summary == null || summary.isBlank());
    }
}
