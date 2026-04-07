package com.solv.wefin.domain.news.article.entity;

import com.solv.wefin.domain.news.article.entity.NewsArticle.RelevanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceStatusTest {

    @Test
    @DisplayName("FINANCIAL 문자열을 정상 변환한다")
    void from_financial() {
        assertThat(RelevanceStatus.from("FINANCIAL")).isEqualTo(RelevanceStatus.FINANCIAL);
    }

    @Test
    @DisplayName("IRRELEVANT 문자열을 정상 변환한다")
    void from_irrelevant() {
        assertThat(RelevanceStatus.from("IRRELEVANT")).isEqualTo(RelevanceStatus.IRRELEVANT);
    }

    @Test
    @DisplayName("소문자/혼합 대소문자도 정상 변환한다")
    void from_caseInsensitive() {
        assertThat(RelevanceStatus.from("financial")).isEqualTo(RelevanceStatus.FINANCIAL);
        assertThat(RelevanceStatus.from("Financial")).isEqualTo(RelevanceStatus.FINANCIAL);
        assertThat(RelevanceStatus.from("irrelevant")).isEqualTo(RelevanceStatus.IRRELEVANT);
    }

    @Test
    @DisplayName("앞뒤 공백이 있어도 정상 변환한다")
    void from_trimmed() {
        assertThat(RelevanceStatus.from("  FINANCIAL  ")).isEqualTo(RelevanceStatus.FINANCIAL);
    }

    @Test
    @DisplayName("null이면 PENDING을 반환한다")
    void from_null() {
        assertThat(RelevanceStatus.from(null)).isEqualTo(RelevanceStatus.PENDING);
    }

    @Test
    @DisplayName("예상 외 값이면 PENDING을 반환한다")
    void from_unexpected() {
        assertThat(RelevanceStatus.from("UNKNOWN")).isEqualTo(RelevanceStatus.PENDING);
        assertThat(RelevanceStatus.from("")).isEqualTo(RelevanceStatus.PENDING);
        assertThat(RelevanceStatus.from("  ")).isEqualTo(RelevanceStatus.PENDING);
    }
}
