package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuRecentTradeApiResponse;

import java.math.BigDecimal;

public record RecentTradeResponse(
        String tradeTime,
        BigDecimal price,
        BigDecimal changePrice,
        String changeSign,
        float changeRate,
        long volume,
        float tradeStrength
) {
    public static RecentTradeResponse from(HantuRecentTradeApiResponse.Output output) {
        return new RecentTradeResponse(
                output.stck_cntg_hour(),
                parseBigDecimal(output.stck_prpr()),
                parseBigDecimal(output.prdy_vrss()),
                output.prdy_vrss_sign(),
                parseFloat(output.prdy_ctrt()),
                parseLong(output.cntg_vol()),
                parseFloat(output.tday_rltv())
        );
    }

    private static BigDecimal parseBigDecimal(String value) {
        return new BigDecimal(value == null || value.isBlank() ? "0" : value);
    }

    private static Long parseLong(String value) {
        return value == null || value.isBlank() ? 0L : Long.parseLong(value);
    }

    private static float parseFloat(String value) {
        return value == null || value.isBlank() ? 0F : Float.parseFloat(value);
    }
}
