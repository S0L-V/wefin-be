package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.global.util.ParseUtils;

import java.math.BigDecimal;

public record PriceResponse(
        String stockCode,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        float changeRate,
        long volume,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice
) {
    public static PriceResponse from(String stockCode, HantuPriceApiResponse.Output output) {
        return new PriceResponse(
                stockCode,
                ParseUtils.parseBigDecimal(output.stck_prpr()),
                ParseUtils.parseBigDecimal(output.prdy_vrss()),
                ParseUtils.parseFloat(output.prdy_ctrt()),
                ParseUtils.parseLong(output.acml_vol()),
                ParseUtils.parseBigDecimal(output.stck_oprc()),
                ParseUtils.parseBigDecimal(output.stck_hgpr()),
                ParseUtils.parseBigDecimal(output.stck_lwpr())
        );
    }
}
