package com.solv.wefin.domain.game.result.dto;

import java.time.OffsetDateTime;

/**
 * AI 분석 리포트 조회용 Domain DTO.
 * 본인이 FINISHED 상태이면 호출 가능. lazy 생성 — 첫 호출 시 OpenAI 호출 후 저장,
 * 이후 호출은 DB 캐시에서 즉시 반환.
 */
public record AnalysisReportInfo(
        String performance,
        String pattern,
        String suggestion,
        OffsetDateTime generatedAt
) {}
