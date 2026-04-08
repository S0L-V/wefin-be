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
                new BigDecimal(output.stck_prpr()),
                new BigDecimal(output.prdy_vrss()),
                output.prdy_vrss_sign(),
                Float.parseFloat(output.prdy_ctrt()),
                Long.parseLong(output.cntg_vol()),
                Float.parseFloat(output.tday_rltv())
        );
    }
}
