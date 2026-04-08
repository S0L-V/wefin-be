package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuRecentTradeApiResponse;
import com.solv.wefin.global.util.ParseUtils;

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
                ParseUtils.parseBigDecimal(output.stck_prpr()),
                ParseUtils.parseBigDecimal(output.prdy_vrss()),
                output.prdy_vrss_sign(),
                ParseUtils.parseFloat(output.prdy_ctrt()),
                ParseUtils.parseLong(output.cntg_vol()),
                ParseUtils.parseFloat(output.tday_rltv())
        );
    }
}
