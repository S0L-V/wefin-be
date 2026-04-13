package com.solv.wefin.domain.game.news.dto;

import java.time.LocalDate;

/**
 * 게임 컨텍스트의 AI 브리핑 조회 결과.
 *
 * @param targetDate     브리핑 대상 날짜 (활성 턴의 날짜)
 * @param marketOverview 시장 개요
 * @param keyIssues      주요 이슈
 * @param investmentHint 투자 힌트
 */
public record BriefingInfo(
        LocalDate targetDate,
        String marketOverview,
        String keyIssues,
        String investmentHint
) {
}
