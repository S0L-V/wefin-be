package com.solv.wefin.domain.trading.market.client.dto;

import java.util.List;

public record HantuRecentTradeApiResponse(
        List<Output> output
) {
    public record Output(
            String stck_cntg_hour,   // 체결 시간
            String stck_prpr,        // 체결가
            String prdy_vrss,        // 전일 대비
            String prdy_vrss_sign,   // 전일 대비 부호
            String cntg_vol,         // 체결 수량
            String tday_rltv,        // 체결강도
            String prdy_ctrt         // 전일 대비율
    ) {}
}
