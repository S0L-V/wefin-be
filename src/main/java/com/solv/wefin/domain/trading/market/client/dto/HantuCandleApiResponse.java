package com.solv.wefin.domain.trading.market.client.dto;

import java.util.List;

public record HantuCandleApiResponse(
        List<Output2> output2
) {
    public record Output2(
            String stck_bsop_date,  // 날짜
            String stck_oprc,       // 시가
            String stck_hgpr,       // 고가
            String stck_lwpr,       // 저가
            String stck_clpr,       // 종가
            String acml_vol         // 거래량
    ) {}
}
