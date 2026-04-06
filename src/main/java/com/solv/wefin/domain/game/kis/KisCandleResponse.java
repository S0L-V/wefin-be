package com.solv.wefin.domain.game.kis;

import java.util.List;

public record KisCandleResponse(
        Output1 output1,
        List<Output2> output2
) {

    public record Output1(
            String hts_kor_isnm  // 종목명
    ) {}

    public record Output2(
            String stck_bsop_date,  // 거래일 (yyyyMMdd)
            String stck_oprc,       // 시가
            String stck_hgpr,       // 고가
            String stck_lwpr,       // 저가
            String stck_clpr,       // 종가
            String acml_vol,        // 거래량
            String prdy_ctrt        // 등락률 (%)
    ) {}
}
