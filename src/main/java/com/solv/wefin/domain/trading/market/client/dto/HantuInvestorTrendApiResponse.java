package com.solv.wefin.domain.trading.market.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 한투 Open API /uapi/domestic-stock/v1/quotations/inquire-investor (TR_ID: FHKST01010900) 응답.
 * 종목별 투자자(외국인/기관/개인) 일별 순매수 수량을 반환한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HantuInvestorTrendApiResponse(List<Output> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            String stck_bsop_date,   // 주식영업일자 (YYYYMMDD)
            String stck_clpr,        // 주식종가
            String prdy_vrss,        // 전일대비
            String prdy_vrss_sign,   // 전일대비 부호 (1:상한,2:상승,3:보합,4:하한,5:하락)
            String prsn_ntby_qty,    // 개인순매수수량
            String frgn_ntby_qty,    // 외국인순매수수량
            String orgn_ntby_qty     // 기관계순매수수량
    ) {}
}
