package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record CandleResponse(
        LocalDate date,
        int openPrice,
        int highPrice,
        int lowPrice,
        int closePrice,
        long volume
) {
    public static CandleResponse from(HantuCandleApiResponse.Output2 output2) {
        return new CandleResponse(
                LocalDate.parse(output2.stck_bsop_date(), DateTimeFormatter.ofPattern("yyyyMMdd")),
                Integer.parseInt(output2.stck_oprc()),
                Integer.parseInt(output2.stck_hgpr()),
                Integer.parseInt(output2.stck_lwpr()),
                Integer.parseInt(output2.stck_clpr()),
                Long.parseLong(output2.acml_vol())
        );
    }

}
