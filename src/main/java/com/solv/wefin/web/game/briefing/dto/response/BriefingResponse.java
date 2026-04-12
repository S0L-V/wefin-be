package com.solv.wefin.web.game.briefing.dto.response;

import com.solv.wefin.domain.game.news.dto.BriefingInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class BriefingResponse {

    private LocalDate targetDate;
    private String briefingText;

    public static BriefingResponse from(BriefingInfo info) {
        return new BriefingResponse(
                info.targetDate(),
                info.briefingText()
        );
    }
}
