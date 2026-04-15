package com.solv.wefin.domain.market.trend.dto;

/**
 * 맞춤 동향 응답의 생성 방식을 명시적으로 구분한다.
 *
 *   @link #MATCHED} — 사용자 관심사와 매칭된 클러스터로 personalized prompt 생성.
 *       프론트는 "내 관심 종목 맞춤 동향" 블록으로 노출.
 *   @link #ACTION_BRIEFING} — 매칭 클러스터가 없어 일반 시장 액션 브리핑으로 폴백.
 *       프론트는 "오늘의 시장 액션" 같은 일반 시장 분석 블록으로 노출.
 *   @link #OVERVIEW_FALLBACK} — 관심사 0개 또는 일반 클러스터도 0건이라 overview 콘텐츠 그대로 반환.
 *       프론트는 별도 personalized 블록을 노출하지 않거나 안내 배너로 처리.
 */
public enum PersonalizationMode {
    MATCHED,
    ACTION_BRIEFING,
    OVERVIEW_FALLBACK
}
