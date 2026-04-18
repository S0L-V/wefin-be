package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DART /api/fnlttSinglAcntAll.json 응답 list[] 내부 항목.
 * 필요한 필드만 매핑 (전체 스펙은 수십 개).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartFinancialItem(
        @JsonProperty("sj_div") String statementDiv,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("account_nm") String accountName,
        @JsonProperty("thstrm_nm") String currentPeriodName,
        @JsonProperty("thstrm_amount") String currentAmount,
        @JsonProperty("frmtrm_nm") String previousPeriodName,
        @JsonProperty("frmtrm_amount") String previousAmount,
        @JsonProperty("bfefrmtrm_nm") String prePreviousPeriodName,
        @JsonProperty("bfefrmtrm_amount") String prePreviousAmount,
        @JsonProperty("currency") String currency
) {
}
