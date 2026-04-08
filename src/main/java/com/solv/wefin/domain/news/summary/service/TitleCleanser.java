package com.solv.wefin.domain.news.summary.service;

import java.util.regex.Pattern;

/**
 * 단독 클러스터 title에서 언론사 태그, 특수 기호, 말줄임표를 제거한다.
 *
 * <p>규칙 기반 클렌징으로, AI 호출 없이 대부분의 제목을 정제할 수 있다.
 * 클렌징 후 결과가 너무 짧으면(MIN_CLEAN_LENGTH 미만) AI fallback이 필요하다는 신호.</p>
 */
public class TitleCleanser {

    static final int MIN_CLEAN_LENGTH = 10;
    static final int MAX_TITLE_LENGTH = 50;

    // [경제D톡스], [속보][단독] 같은 접두사 대괄호를 1개 이상 연속 제거
    // 제목 시작 부분의 [...][...] 만 제거 (본문 중간의 [삼성전자] 같은 건 유지)
    private static final Pattern BRACKET_PREFIX = Pattern.compile("^(\\s*\\[.*?])+\\s*");

    // 특수 기호 제거: ①②③④⑤⑥⑦⑧⑨⑩☞★▶▷◆◇■□●○△▲▽▼◁◀
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[①②③④⑤⑥⑦⑧⑨⑩☞★▶▷◆◇■□●○△▲▽▼◁◀]");

    // 시작 말줄임표 제거: "...불안 부추기는"
    private static final Pattern LEADING_ELLIPSIS = Pattern.compile("^\\.{2,}\\s*");

    // 끝 말줄임표 정리: "가짜뉴스까지 기..." → "가짜뉴스까지 기"
    private static final Pattern TRAILING_ELLIPSIS = Pattern.compile("\\s*\\.{2,}$");

    // 연속 공백 정리
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");

    /**
     * title을 정제한다.
     *
     * @return 정제된 title. null이나 빈 문자열이 들어오면 그대로 반환.
     */
    public static String cleanse(String title) {
        if (title == null) {
            return null;
        }
        if (title.isBlank()) {
            return "";
        }

        String result = title;
        result = BRACKET_PREFIX.matcher(result).replaceAll("");
        result = SPECIAL_CHARS.matcher(result).replaceAll("");
        result = LEADING_ELLIPSIS.matcher(result).replaceAll("");
        result = TRAILING_ELLIPSIS.matcher(result).replaceAll("");
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");
        result = result.trim();

        return result;
    }

    /**
     * 클렌징 후 AI fallback이 필요한지 판단한다.
     *
     * @return true면 AI로 title 재생성 필요
     */
    /**
     * AI fallback이 필요한지 판단한다.
     *
     * @param cleansedTitle 클렌징된 title
     * @param originalTitle 클렌징 전 원본 title (말줄임표 절단 감지용)
     * @return true면 AI로 title 재생성 필요
     */
    public static boolean needsAiFallback(String cleansedTitle, String originalTitle) {
        if (cleansedTitle == null || cleansedTitle.length() < MIN_CLEAN_LENGTH) {
            return true;
        }
        // 원본이 "..." 또는 "…"로 끝나면 뉴스 소스가 제목을 잘랐다는 의미 → AI가 완결된 title 생성
        return originalTitle != null && (originalTitle.endsWith("...") || originalTitle.endsWith("…"));
    }

    /**
     * AI가 생성한 title을 검증 + 정제한다.
     * 클렌징 적용 후 50자 초과면 안전 절단한다.
     *
     * @return 유효한 title, 검증 실패 시 null
     */
    public static String sanitizeAiTitle(String aiTitle) {
        if (aiTitle == null || aiTitle.isBlank()) {
            return null;
        }

        String sanitized = cleanse(aiTitle);

        if (sanitized.isEmpty() || sanitized.length() < MIN_CLEAN_LENGTH) {
            return null;
        }

        if (sanitized.length() > MAX_TITLE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TITLE_LENGTH);
        }

        return sanitized;
    }
}
