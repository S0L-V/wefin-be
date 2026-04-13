package com.solv.wefin.web.game.briefing.dto.response;

import com.solv.wefin.domain.game.news.dto.BriefingInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class BriefingResponse {

    private LocalDate targetDate;
    private String marketOverview;
    private String keyIssues;
    private String investmentHint;

    public static BriefingResponse from(BriefingInfo info) {
        return new BriefingResponse(
                info.targetDate(),
                info.marketOverview(),
                info.keyIssues(),
                info.investmentHint()
        );
    }
}
