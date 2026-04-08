package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;
import com.solv.wefin.global.util.ParseUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record CandleResponse(
        LocalDate date,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        long volume
) {
    public static CandleResponse from(HantuCandleApiResponse.Output2 output2) {
        return new CandleResponse(
                LocalDate.parse(output2.stck_bsop_date(), DateTimeFormatter.ofPattern("yyyyMMdd")),
                ParseUtils.parseBigDecimal(output2.stck_oprc()),
                ParseUtils.parseBigDecimal(output2.stck_hgpr()),
                ParseUtils.parseBigDecimal(output2.stck_lwpr()),
                ParseUtils.parseBigDecimal(output2.stck_clpr()),
                ParseUtils.parseLong(output2.acml_vol())
        );
    }
}
