package com.solv.wefin.domain.news.tagging.dto;

import lombok.Getter;

import java.util.List;

/**
 * OpenAI 태깅 API의 구조화된 응답 결과를 담는 DTO
 */
@Getter
public class TaggingResult {

    private List<TagItem> stocks;
    private List<TagItem> sectors;
    private List<TagItem> topics;
    private String summary;

    /**
     * 금융 관련성 판정
     */
    private String relevance;

    public TaggingResult() {
        this.stocks = List.of();
        this.sectors = List.of();
        this.topics = List.of();
    }

    /**
     * 전체 태그가 비어있는지 확인한다.
     */
    public boolean isEmpty() {
        return (stocks == null || stocks.isEmpty())
                && (sectors == null || sectors.isEmpty())
                && (topics == null || topics.isEmpty());
    }

    @Getter
    public static class TagItem {
        private String code;
        private String name;
    }
}
