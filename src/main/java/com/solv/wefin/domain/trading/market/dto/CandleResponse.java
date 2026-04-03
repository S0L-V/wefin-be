package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;

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
                parseBigDecimal(output2.stck_oprc()),
                parseBigDecimal(output2.stck_hgpr()),
                parseBigDecimal(output2.stck_lwpr()),
                parseBigDecimal(output2.stck_clpr()),
                parseLong(output2.acml_vol())
        );
    }

    private static BigDecimal parseBigDecimal(String value) {
        return new BigDecimal(value.isBlank() ? "0" : value);
    }

    private static Long parseLong(String value) {
        return value.isBlank() ? 0L : Long.parseLong(value);
    }

}
