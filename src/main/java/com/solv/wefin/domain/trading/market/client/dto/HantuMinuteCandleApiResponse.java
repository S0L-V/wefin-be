package com.solv.wefin.domain.trading.market.client.dto;

import java.util.List;

public record HantuMinuteCandleApiResponse(
        Output1 output1,
        List<Output2> output2
) {
    public record Output1(String rt_cd, String msg_cd, String msg1) {}

    public record Output2(
            String stck_bsop_date,  // 영업일자 (YYYYMMDD)
            String stck_cntg_hour,  // 체결시간 (HHMMSS)
            String stck_prpr,       // 현재가(종가)
            String stck_oprc,       // 시가
            String stck_hgpr,       // 고가
            String stck_lwpr,       // 저가
            String cntg_vol         // 체결 거래량
    ) {}
}
