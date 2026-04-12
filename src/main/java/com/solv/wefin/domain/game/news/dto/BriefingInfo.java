package com.solv.wefin.domain.game.news.dto;

import java.time.LocalDate;

/**
 * 게임 컨텍스트의 AI 브리핑 조회 결과.
 *
 * @param targetDate   브리핑 대상 날짜 (활성 턴의 날짜)
 * @param briefingText 캐시 또는 OpenAI 생성 결과 텍스트
 */
public record BriefingInfo(LocalDate targetDate, String briefingText) {
}
