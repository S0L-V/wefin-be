package com.solv.wefin.domain.news.tagging.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaggingResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("태그와 relevance가 모두 없으면 isEmpty = true")
    void isEmpty_noTagsNoRelevance() throws Exception {
        TaggingResult result = parse("""
                { "stocks": [], "sectors": [], "topics": [] }
                """);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("태그는 없지만 relevance가 있으면 isEmpty = false (비금융 기사 정상 케이스)")
    void isEmpty_noTagsWithRelevance() throws Exception {
        TaggingResult result = parse("""
                { "stocks": [], "sectors": [], "topics": [], "relevance": "IRRELEVANT" }
                """);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("태그가 있으면 relevance 유무와 관계없이 isEmpty = false")
    void isEmpty_withTags() throws Exception {
        TaggingResult result = parse("""
                { "stocks": [{"code": "005930", "name": "삼성전자"}], "sectors": [], "topics": [] }
                """);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("relevance가 빈 문자열이면 isEmpty에 포함되지 않는다")
    void isEmpty_blankRelevance() throws Exception {
        TaggingResult result = parse("""
                { "stocks": [], "sectors": [], "topics": [], "relevance": "  " }
                """);
        assertThat(result.isEmpty()).isTrue();
    }

    private TaggingResult parse(String json) throws Exception {
        return objectMapper.readValue(json, TaggingResult.class);
    }
}
