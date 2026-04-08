package com.solv.wefin.domain.news.summary.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleCleanserTest {

    @Test
    @DisplayName("[경제D톡스] 접두사를 제거한다")
    void cleanse_bracketPrefix() {
        assertThat(TitleCleanser.cleanse("[경제D톡스] 1500원 넘는 고환율 지속"))
                .isEqualTo("1500원 넘는 고환율 지속");
    }

    @Test
    @DisplayName("[기자수첩] 접두사를 제거한다")
    void cleanse_reporterNote() {
        assertThat(TitleCleanser.cleanse("[기자수첩] '뉴노멀'이라는 말장난...1400·1500원 환율은 '비정상'이다"))
                .isEqualTo("'뉴노멀'이라는 말장난...1400·1500원 환율은 '비정상'이다");
    }

    @Test
    @DisplayName("[전쟁이 삼킨 증시①] 숫자 기호가 포함된 접두사도 제거한다")
    void cleanse_bracketWithSymbol() {
        assertThat(TitleCleanser.cleanse("[전쟁이 삼킨 증시①] 공포 정점 지났나"))
                .isEqualTo("공포 정점 지났나");
    }

    @Test
    @DisplayName("특수 기호 ①②③를 제거한다")
    void cleanse_specialChars() {
        assertThat(TitleCleanser.cleanse("반도체 TOP3① 삼성전자 실적"))
                .isEqualTo("반도체 TOP3 삼성전자 실적");
    }

    @Test
    @DisplayName("끝 말줄임표를 제거한다")
    void cleanse_trailingEllipsis() {
        assertThat(TitleCleanser.cleanse("불안 부추기는 가짜뉴스까지 기..."))
                .isEqualTo("불안 부추기는 가짜뉴스까지 기");
    }

    @Test
    @DisplayName("시작 말줄임표를 제거한다")
    void cleanse_leadingEllipsis() {
        assertThat(TitleCleanser.cleanse("...삼성전자 실적 발표"))
                .isEqualTo("삼성전자 실적 발표");
    }

    @Test
    @DisplayName("연속 공백을 하나로 줄인다")
    void cleanse_multipleSpaces() {
        assertThat(TitleCleanser.cleanse("삼성전자   실적   발표"))
                .isEqualTo("삼성전자 실적 발표");
    }

    @Test
    @DisplayName("이미 깨끗한 제목은 그대로 반환한다")
    void cleanse_alreadyClean() {
        String clean = "중동발 유가 상승, 인플레이션 우려 확산";
        assertThat(TitleCleanser.cleanse(clean)).isEqualTo(clean);
    }

    @Test
    @DisplayName("null은 null, blank는 빈 문자열로 반환한다")
    void cleanse_nullOrBlank() {
        assertThat(TitleCleanser.cleanse(null)).isNull();
        assertThat(TitleCleanser.cleanse("")).isEqualTo("");
        assertThat(TitleCleanser.cleanse("  ")).isEqualTo("");
    }

    @Test
    @DisplayName("복합 패턴 — 접두사 + 특수기호 + 말줄임표")
    void cleanse_combined() {
        assertThat(TitleCleanser.cleanse("[HD포토] ☞ 벚꽃 만개한 정읍천 벚꼴길..."))
                .isEqualTo("벚꽃 만개한 정읍천 벚꼴길");
    }

    @Test
    @DisplayName("10자 미만이면 AI fallback 필요로 판단한다")
    void needsAiFallback_shortTitle() {
        assertThat(TitleCleanser.needsAiFallback("미국환율")).isTrue();
        assertThat(TitleCleanser.needsAiFallback(null)).isTrue();
    }

    @Test
    @DisplayName("10자 이상이면 AI fallback 불필요")
    void needsAiFallback_longEnough() {
        assertThat(TitleCleanser.needsAiFallback("중동발 유가 상승, 인플레이션 우려 확산")).isFalse();
    }
}
