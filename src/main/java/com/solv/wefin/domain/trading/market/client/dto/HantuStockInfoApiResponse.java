package com.solv.wefin.domain.trading.market.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 한투 Open API /uapi/domestic-stock/v1/quotations/inquire-price (TR_ID: FHKST01010100) 응답 중
 * 종목 기본정보(시가총액/상장주식수/외국인소진율)에 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HantuStockInfoApiResponse(Output output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            String hts_avls,        // 시가총액 (단위: 억원)
            String lstn_stcn,       // 상장주식수 (주)
            String hts_frgn_ehrt,   // 외국인소진율 (%)
            String per,             // PER
            String pbr,             // PBR
            String eps              // EPS
    ) {
    }
}
