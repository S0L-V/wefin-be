package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DART OpenAPI /api/company.json 응답 원본 DTO.
 * 필드는 DART 스펙(snake_case) 그대로 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartCompanyApiResponse(
        String status,
        String message,
        @JsonProperty("corp_name") String corpName,
        @JsonProperty("corp_name_eng") String corpNameEng,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("ceo_nm") String ceoName,
        @JsonProperty("corp_cls") String corpCls,
        @JsonProperty("jurir_no") String jurirNo,
        @JsonProperty("bizr_no") String bizrNo,
        @JsonProperty("adres") String address,
        @JsonProperty("hm_url") String homepageUrl,
        @JsonProperty("ir_url") String irUrl,
        @JsonProperty("phn_no") String phoneNo,
        @JsonProperty("fax_no") String faxNo,
        @JsonProperty("induty_code") String indutyCode,
        @JsonProperty("est_dt") String establishedDate,
        @JsonProperty("acc_mt") String accountingMonth
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isNoData() {
        return "013".equals(status);
    }
}
