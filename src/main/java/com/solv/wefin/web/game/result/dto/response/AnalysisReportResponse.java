package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.result.dto.AnalysisReportInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class AnalysisReportResponse {

    private String performance;
    private String pattern;
    private String suggestion;
    private OffsetDateTime generatedAt;

    public static AnalysisReportResponse from(AnalysisReportInfo info) {
        return new AnalysisReportResponse(
                info.performance(),
                info.pattern(),
                info.suggestion(),
                info.generatedAt());
    }
}
