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
    @DisplayName("시작 말줄임표를 제거한다 (ASCII)")
    void cleanse_leadingEllipsis() {
        assertThat(TitleCleanser.cleanse("...삼성전자 실적 발표"))
                .isEqualTo("삼성전자 실적 발표");
    }

    @Test
    @DisplayName("시작 Unicode 말줄임표를 제거한다")
    void cleanse_leadingUnicodeEllipsis() {
        assertThat(TitleCleanser.cleanse("…삼성전자 실적 발표"))
                .isEqualTo("삼성전자 실적 발표");
    }

    @Test
    @DisplayName("끝 Unicode 말줄임표를 제거한다")
    void cleanse_trailingUnicodeEllipsis() {
        assertThat(TitleCleanser.cleanse("가짜뉴스까지 기…"))
                .isEqualTo("가짜뉴스까지 기");
    }

    @Test
    @DisplayName("끝 말줄임표 뒤 공백도 함께 제거한다")
    void cleanse_trailingEllipsisWithSpaces() {
        assertThat(TitleCleanser.cleanse("삼성전자 실적 발표...  "))
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
    @DisplayName("null은 null, blank/공백만 있는 문자열은 빈 문자열로 반환한다")
    void cleanse_nullOrBlank() {
        assertThat(TitleCleanser.cleanse(null)).isNull();
        assertThat(TitleCleanser.cleanse("")).isEqualTo("");
        assertThat(TitleCleanser.cleanse(" ")).isEqualTo("");
        assertThat(TitleCleanser.cleanse("  ")).isEqualTo("");
    }

    @Test
    @DisplayName("복합 패턴 — 접두사 + 특수기호 + 말줄임표")
    void cleanse_combined() {
        assertThat(TitleCleanser.cleanse("[HD포토] ☞ 벚꽃 만개한 정읍천 벚꼴길..."))
                .isEqualTo("벚꽃 만개한 정읍천 벚꼴길");
    }

    @Test
    @DisplayName("[속보][단독] 연속 대괄호 접두사를 모두 제거한다")
    void cleanse_consecutiveBrackets() {
        assertThat(TitleCleanser.cleanse("[속보][단독] 삼성전자 1분기 실적 발표"))
                .isEqualTo("삼성전자 1분기 실적 발표");
    }

    @Test
    @DisplayName("sanitizeAiTitle — 정상 title은 클렌징 후 반환한다")
    void sanitizeAiTitle_normal() {
        assertThat(TitleCleanser.sanitizeAiTitle("삼성전자 1분기 실적, 시장 기대 상회"))
                .isEqualTo("삼성전자 1분기 실적, 시장 기대 상회");
    }

    @Test
    @DisplayName("sanitizeAiTitle — AI가 대괄호를 포함해도 제거된다")
    void sanitizeAiTitle_withBracket() {
        assertThat(TitleCleanser.sanitizeAiTitle("[요약] 삼성전자 실적 발표"))
                .isEqualTo("삼성전자 실적 발표");
    }

    @Test
    @DisplayName("sanitizeAiTitle — 50자 초과면 절단한다")
    void sanitizeAiTitle_truncate() {
        String longTitle = "삼성전자가 2026년 1분기 영업이익 6조 6천억원을 기록하며 시장 기대를 크게 상회하는 실적을 발표했다";
        String result = TitleCleanser.sanitizeAiTitle(longTitle);
        assertThat(result).isNotNull();
        assertThat(result.length()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("sanitizeAiTitle — null/blank/짧은 결과는 null 반환")
    void sanitizeAiTitle_invalid() {
        assertThat(TitleCleanser.sanitizeAiTitle(null)).isNull();
        assertThat(TitleCleanser.sanitizeAiTitle("")).isNull();
        assertThat(TitleCleanser.sanitizeAiTitle("짧음")).isNull();
    }

    @Test
    @DisplayName("10자 미만이면 AI fallback 필요로 판단한다")
    void needsAiFallback_shortTitle() {
        assertThat(TitleCleanser.needsAiFallback("미국환율", "미국환율")).isTrue();
        assertThat(TitleCleanser.needsAiFallback(null, null)).isTrue();
    }

    @Test
    @DisplayName("10자 이상 + 원본에 말줄임표 없으면 AI fallback 불필요")
    void needsAiFallback_longEnough() {
        assertThat(TitleCleanser.needsAiFallback("중동발 유가 상승, 인플레이션 우려 확산", "중동발 유가 상승, 인플레이션 우려 확산")).isFalse();
    }

    @Test
    @DisplayName("10자 이상이라도 원본이 말줄임표로 끝나면 AI fallback 필요 (절단 감지)")
    void needsAiFallback_truncatedOriginal() {
        assertThat(TitleCleanser.needsAiFallback("불안 부추기는 가짜뉴스까지 기", "불안 부추기는 가짜뉴스까지 기...")).isTrue();
        assertThat(TitleCleanser.needsAiFallback("삼성전자 실적 발표", "삼성전자 실적 발표…")).isTrue();
        assertThat(TitleCleanser.needsAiFallback("삼성전자 실적 발표", "삼성전자 실적 발표..")).isTrue();
        assertThat(TitleCleanser.needsAiFallback("삼성전자 실적 발표", "삼성전자 실적 발표…  ")).isTrue();
    }
}
