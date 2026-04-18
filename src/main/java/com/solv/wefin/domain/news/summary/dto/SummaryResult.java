package com.solv.wefin.domain.news.summary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 요약 생성 응답 DTO
 *
 * 다건 클러스터: title + leadSummary + sections 구조로 응답
 * 단독 클러스터: AI 호출 없이 기사 제목/요약을 직접 사용하므로 이 DTO를 거치지 않는다
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryResult {

    private String title;       // 클러스터 대표 제목 (50자 이내)
    private String leadSummary; // Lead 요약 (피드 목록 카드에서 사용, 200~400자)
    private List<SectionItem> sections; // 상세 페이지용 섹션 목록
    private List<String> suggestedQuestions; // AI 추천 질문 (3개)

    /**
     * title 또는 leadSummary가 비어있으면 유효하지 않은 응답으로 판단한다
     */
    public boolean isEmpty() {
        return (title == null || title.isBlank()) || (leadSummary == null || leadSummary.isBlank());
    }

    /**
     * 섹션이 존재하는지 확인한다
     */
    public boolean hasSections() {
        return sections != null && !sections.isEmpty();
    }

    /**
     * 추천 질문이 존재하는지 확인한다
     */
    public boolean hasQuestions() {
        return suggestedQuestions != null && !suggestedQuestions.isEmpty();
    }

    /**
     * 상세 페이지용 개별 섹션 (소제목 + 단락 + 근거 기사 인덱스)
     */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SectionItem {

        private String heading;
        private String body;
        private List<Integer> sourceArticleIndices;

        /**
         * heading과 body가 모두 존재해야 유효한 섹션이다
         */
        public boolean isValid() {
            return heading != null && !heading.isBlank()
                    && body != null && !body.isBlank();
        }

        /**
         * 근거 기사 인덱스가 존재하는지 확인한다
         */
        public boolean hasSources() {
            return sourceArticleIndices != null && !sourceArticleIndices.isEmpty();
        }
    }
}
