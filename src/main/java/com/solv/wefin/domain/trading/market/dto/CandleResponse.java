package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuMinuteCandleApiResponse;
import com.solv.wefin.global.util.ParseUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record CandleResponse(
        LocalDateTime date,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        long volume
) {
    // 일봉/주봉/월봉용
    public static CandleResponse from(HantuCandleApiResponse.Output2 output2) {
        return new CandleResponse(
                LocalDate.parse(output2.stck_bsop_date(), DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .atStartOfDay(),
                ParseUtils.parseBigDecimal(output2.stck_oprc()),
                ParseUtils.parseBigDecimal(output2.stck_hgpr()),
                ParseUtils.parseBigDecimal(output2.stck_lwpr()),
                ParseUtils.parseBigDecimal(output2.stck_clpr()),
                ParseUtils.parseLong(output2.acml_vol())
        );
    }

    // 분봉용
    public static CandleResponse fromMinute(HantuMinuteCandleApiResponse.Output2 output2) {
        // "20260413" + "093000" → LocalDateTime
        LocalDate date = LocalDate.parse(output2.stck_bsop_date(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        int hour = Integer.parseInt(output2.stck_cntg_hour().substring(0, 2));
        int minute = Integer.parseInt(output2.stck_cntg_hour().substring(2, 4));
        LocalDateTime dateTime = date.atTime(hour, minute);

        return new CandleResponse(
                dateTime,
                ParseUtils.parseBigDecimal(output2.stck_oprc()),
                ParseUtils.parseBigDecimal(output2.stck_hgpr()),
                ParseUtils.parseBigDecimal(output2.stck_lwpr()),
                ParseUtils.parseBigDecimal(output2.stck_prpr()),
                ParseUtils.parseLong(output2.cntg_vol())
        );
    }
}
